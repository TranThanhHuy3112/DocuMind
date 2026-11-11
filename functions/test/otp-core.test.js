"use strict";

const {test} = require("node:test");
const assert = require("node:assert/strict");
const {evaluateChallenge} = require("../otp-core");

const now = 1_000_000;
const hash = "ab".repeat(32);
const wrong = "cd".repeat(32);
const challenge = {email: "user@example.com", hash, expiresAt: now + 60_000,
  lockedUntil: 0, attempts: 0};

test("matching OTP succeeds only for the challenged email before expiry", () => {
  assert.deepEqual(evaluateChallenge(challenge, challenge.email, hash, now, 5, 180_000),
      {verified: true});
  assert.equal(evaluateChallenge(challenge, "other@example.com", hash,
      now, 5, 180_000).error, "MISSING");
  assert.equal(evaluateChallenge(challenge, challenge.email, hash,
      challenge.expiresAt, 5, 180_000).error, "OTP_EXPIRED");
});

test("five wrong attempts lock even if the sixth code is correct", () => {
  let state = {...challenge};
  for (let attempt = 1; attempt <= 5; attempt++) {
    const decision = evaluateChallenge(state, state.email, wrong, now, 5, 180_000);
    assert.equal(decision.attempts, attempt);
    assert.equal(decision.attemptsRemaining, 5 - attempt);
    state = {...state, attempts: decision.attempts, lockedUntil: decision.retryAt};
  }
  assert.equal(evaluateChallenge(state, state.email, hash,
      now + 1, 5, 180_000).error, "OTP_LOCKED");
});

test("malformed non-string challenge hash returns MISSING without throwing", () => {
  const malformed = {...challenge, hash: null};
  assert.equal(evaluateChallenge(malformed, challenge.email, hash,
      now, 5, 180_000).error, "MISSING");
});

test("expired lock resets previous attempts instead of relocking on first mistake", () => {
  const expiredLock = {
    ...challenge,
    attempts: 5,
    lockedUntil: now - 1,
    expiresAt: now + 60_000,
  };
  const decision = evaluateChallenge(expiredLock, challenge.email, wrong, now, 5, 180_000);
  assert.equal(decision.error, "INVALID_OTP");
  assert.equal(decision.attempts, 1);
  assert.equal(decision.attemptsRemaining, 4);
});
