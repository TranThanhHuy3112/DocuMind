"use strict";

const crypto = require("node:crypto");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore} = require("firebase-admin/firestore");
const nodemailer = require("nodemailer");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret, defineJsonSecret} = require("firebase-functions/params");
const {evaluateChallenge} = require("./otp-core");

initializeApp();
const db = getFirestore();
const auth = getAuth();
const mailConfig = defineJsonSecret("OTP_SMTP_CONFIG");
const pepper = defineSecret("OTP_PEPPER");
const REGION = "asia-southeast1";
const TTL_MS = 60_000;
const LOCK_MS = 180_000;
const MAX_ATTEMPTS = 5;
const RESET_RATE_WINDOW_MS = 3_600_000;
const RESET_RATE_LIMIT = 120;
const EMAIL_RESET_LIMIT = 5;

function requireUser(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "SIGN_IN_REQUIRED");
  return request.auth.uid;
}

function hashCode(uid, code) {
  return crypto.createHmac("sha256", pepper.value()).update(`${uid}:${code}`).digest("hex");
}

function resetKey(email) {
  return crypto.createHmac("sha256", pepper.value()).update(`reset:${email}`).digest("hex");
}

function normalizeEmail(value) {
  if (typeof value !== "string") throw new HttpsError("invalid-argument", "INVALID_EMAIL");
  const email = value.trim().toLowerCase();
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new HttpsError("invalid-argument", "INVALID_EMAIL");
  }
  return email;
}

function strongPassword(value) {
  return typeof value === "string" && value.length >= 8 && value.length <= 128 &&
      /\p{Lu}/u.test(value) && /\p{Ll}/u.test(value) && /\p{Nd}/u.test(value);
}

async function sendCodeMail(email, code, purpose) {
  const config = mailConfig.value();
  if (!config || !config.host || !config.user || !config.password || !config.from) {
    throw new Error("SMTP_CONFIG_MISSING");
  }
  const port = Number(config.port || 465);
  const transport = nodemailer.createTransport({
    host: config.host, port, secure: port === 465,
    auth: {user: config.user, pass: config.password},
  });
  await transport.sendMail({
    from: config.from, to: email,
    subject: purpose === "reset" ? "Mã đặt lại mật khẩu DocuMind" : "Mã xác minh DocuMind",
    text: purpose === "reset"
        ? `Mã đặt lại mật khẩu DocuMind của bạn: ${code}. Mã hết hạn sau 60 giây.`
        : `Mã xác minh DocuMind của bạn: ${code}. Mã hết hạn sau 60 giây.`,
  });
}

async function consumeChallenge(ref, email, code, hashId, missingError) {
  const verdict = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const challenge = snapshot.data();
    const now = Date.now();
    const result = evaluateChallenge(challenge, email, hashCode(hashId, code),
        now, MAX_ATTEMPTS, LOCK_MS);
    if (result.error === "MISSING") throw new HttpsError("not-found", missingError);
    if (result.error === "OTP_EXPIRED") {
      throw new HttpsError("deadline-exceeded", "OTP_EXPIRED");
    }
    if (result.error === "OTP_LOCKED" && result.attempts === undefined) {
      throw new HttpsError("resource-exhausted", "OTP_LOCKED",
          {retryAt: result.retryAt});
    }
    if (result.error) {
      transaction.update(ref, {attempts: result.attempts, lockedUntil: result.retryAt});
      return result;
    }
    transaction.delete(ref);
    return {challenge};
  });
  if (verdict.error) {
    throw new HttpsError(verdict.error === "OTP_LOCKED"
        ? "resource-exhausted" : "invalid-argument", verdict.error, verdict);
  }
  return verdict.challenge;
}

async function limitResetRequests(request) {
  const ip = request.rawRequest &&
      (request.rawRequest.ip || (request.rawRequest.socket &&
          request.rawRequest.socket.remoteAddress));
  if (!ip) throw new HttpsError("unavailable", "REQUEST_SOURCE_UNKNOWN");
  const ipKey = crypto.createHmac("sha256", pepper.value()).update(`ip:${ip}`).digest("hex");
  const ref = db.collection("_otpRequestLimits").doc(ipKey);
  const allowed = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const old = snapshot.data();
    const now = Date.now();
    const count = old && old.windowStart + RESET_RATE_WINDOW_MS > now ? old.count + 1 : 1;
    const windowStart = count === 1 ? now : old.windowStart;
    if (count > RESET_RATE_LIMIT) return false;
    transaction.set(ref, {count, windowStart});
    return true;
  });
  if (!allowed) throw new HttpsError("resource-exhausted", "RESET_RATE_LIMIT");
}

exports.sendVerificationOtp = onCall(
    {region: REGION, secrets: [mailConfig, pepper], enforceAppCheck: true,
      maxInstances: 2}, async (request) => {
      const uid = requireUser(request);
      const user = await auth.getUser(uid);
      if (!user.email) throw new HttpsError("failed-precondition", "EMAIL_REQUIRED");
      if (user.emailVerified) throw new HttpsError("failed-precondition", "ALREADY_VERIFIED");
      const now = Date.now();
      const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, "0");
      const ref = db.collection("_otpChallenges").doc(uid);
      await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(ref);
        const old = snapshot.data();
        if (old && old.lockedUntil > now) {
          throw new HttpsError("resource-exhausted", "OTP_LOCKED",
              {retryAt: old.lockedUntil});
        }
        if (old && old.sentAt + TTL_MS > now) {
          throw new HttpsError("resource-exhausted", "OTP_COOLDOWN",
              {retryAt: old.sentAt + TTL_MS});
        }
        transaction.set(ref, {
          hash: hashCode(uid, code), email: user.email, sentAt: now,
          expiresAt: now + TTL_MS, attempts: 0, lockedUntil: 0,
        });
      });
      try {
        await sendCodeMail(user.email, code, "verify");
      } catch (error) {
        await ref.delete();
        throw new HttpsError("unavailable", "EMAIL_SEND_FAILED");
      }
      return {expiresAt: now + TTL_MS};
    });

exports.verifyEmailOtp = onCall(
    {region: REGION, secrets: [pepper], enforceAppCheck: true,
      maxInstances: 2}, async (request) => {
      const uid = requireUser(request);
      const code = request.data && request.data.code;
      if (typeof code !== "string" || !/^[0-9]{6}$/.test(code)) {
        throw new HttpsError("invalid-argument", "INVALID_OTP");
      }
      const user = await auth.getUser(uid);
      const ref = db.collection("_otpChallenges").doc(uid);
      await consumeChallenge(ref, user.email, code, uid, "NO_PENDING_REGISTRATION");
      await auth.updateUser(uid, {emailVerified: true});
      return {verified: true};
    });

exports.requestPasswordResetOtp = onCall(
    {region: REGION, secrets: [mailConfig, pepper], enforceAppCheck: true,
      maxInstances: 2}, async (request) => {
      const email = normalizeEmail(request.data && request.data.email);
      await limitResetRequests(request);
      const now = Date.now();
      const result = {expiresAt: now + TTL_MS};
      let user;
      try {
        user = await auth.getUserByEmail(email);
      } catch (error) {
        if (error.code === "auth/user-not-found") return result;
        throw error;
      }
      const key = resetKey(email);
      const ref = db.collection("_passwordResetChallenges").doc(key);
      const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, "0");
      const shouldSend = await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(ref);
        const old = snapshot.data();
        if (old && (old.lockedUntil > now || old.sentAt + TTL_MS > now)) return false;
        const withinWindow = old && old.windowStart + RESET_RATE_WINDOW_MS > now;
        const sendCount = withinWindow ? (old.sendCount || 0) + 1 : 1;
        if (sendCount > EMAIL_RESET_LIMIT) return false;
        transaction.set(ref, {uid: user.uid, email, hash: hashCode(key, code),
          sentAt: now, expiresAt: now + TTL_MS, attempts: 0, lockedUntil: 0,
          sendCount, windowStart: withinWindow ? old.windowStart : now});
        return true;
      });
      if (!shouldSend) return result;
      try {
        await sendCodeMail(email, code, "reset");
      } catch (error) {
        await ref.delete();
        throw new HttpsError("unavailable", "EMAIL_SEND_FAILED");
      }
      return result;
    });

exports.completePasswordResetOtp = onCall(
    {region: REGION, secrets: [pepper], enforceAppCheck: true,
      maxInstances: 2}, async (request) => {
      const email = normalizeEmail(request.data && request.data.email);
      const code = request.data && request.data.code;
      const password = request.data && request.data.password;
      if (typeof code !== "string" || !/^[0-9]{6}$/.test(code)) {
        throw new HttpsError("invalid-argument", "INVALID_OTP");
      }
      if (!strongPassword(password)) throw new HttpsError("invalid-argument", "WEAK_PASSWORD");
      const key = resetKey(email);
      const ref = db.collection("_passwordResetChallenges").doc(key);
      const challenge = await consumeChallenge(ref, email, code, key, "NO_PENDING_RESET");
      const user = await auth.getUser(challenge.uid);
      if (user.email !== email) throw new HttpsError("failed-precondition", "NO_PENDING_RESET");
      await auth.updateUser(user.uid, {password, emailVerified: true});
      await auth.revokeRefreshTokens(user.uid);
      return {reset: true};
    });
