"use strict";

const crypto = require("node:crypto");

/** A pure decision function; callers commit attempt updates before reporting an error. */
function evaluateChallenge(challenge, email, expectedHash, now, maxAttempts, lockMs) {
  if (!challenge || challenge.email !== email) return {error: "MISSING"};
  if (challenge.lockedUntil > now) {
    return {error: "OTP_LOCKED", retryAt: challenge.lockedUntil};
  }
  if (challenge.expiresAt <= now) return {error: "OTP_EXPIRED"};
  if (typeof challenge.hash !== "string" || typeof expectedHash !== "string") {
    return {error: "MISSING"};
  }
  const priorAttempts = challenge.lockedUntil > 0 && challenge.lockedUntil <= now
      ? 0 : (challenge.attempts || 0);
  const actual = Buffer.from(challenge.hash, "hex");
  const expected = Buffer.from(expectedHash, "hex");
  if (actual.length !== expected.length || !crypto.timingSafeEqual(actual, expected)) {
    const attempts = priorAttempts + 1;
    const retryAt = attempts >= maxAttempts ? now + lockMs : 0;
    return {error: retryAt ? "OTP_LOCKED" : "INVALID_OTP", attempts,
      attemptsRemaining: Math.max(0, maxAttempts - attempts), retryAt};
  }
  return {verified: true};
}

module.exports = {evaluateChallenge};
