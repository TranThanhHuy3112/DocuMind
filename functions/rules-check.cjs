"use strict";

const fs = require("node:fs");
const path = require("node:path");
const {initializeTestEnvironment, assertSucceeds, assertFails} =
    require("@firebase/rules-unit-testing");
const {doc, getDoc, setDoc, updateDoc, deleteDoc, arrayUnion} = require("firebase/firestore");
const {ref, uploadString} = require("firebase/storage");

async function main() {
  const root = path.resolve(__dirname, "..");
  const env = await initializeTestEnvironment({
    projectId: "demo-documind",
    firestore: {rules: fs.readFileSync(path.join(root, "firestore.rules"), "utf8")},
    storage: {rules: fs.readFileSync(path.join(root, "storage.rules"), "utf8")},
  });
  try {
    const alice = env.authenticatedContext("alice", {email_verified: true, name: "Alice"});
    const bob = env.authenticatedContext("bob", {email_verified: true, name: "Bob"});
    const unverified = env.authenticatedContext("alice", {email_verified: false});
    const guest = env.unauthenticatedContext();
    const profile = {fullName: "Alice", email: "alice@example.com", createdAt: 1};
    await assertSucceeds(setDoc(doc(alice.firestore(), "users/alice"), profile));
    await assertFails(setDoc(doc(bob.firestore(), "users/alice"), profile));
    await assertSucceeds(setDoc(doc(alice.firestore(), "users/alice/documents/doc1"),
        {name: "paper.pdf"}));
    await assertFails(getDoc(doc(bob.firestore(), "users/alice/documents/doc1")));
    await assertFails(getDoc(doc(guest.firestore(), "users/alice/documents/doc1")));
    await assertFails(getDoc(doc(unverified.firestore(), "users/alice/documents/doc1")));
    await assertFails(getDoc(doc(alice.firestore(), "_otpChallenges/alice")));
    await assertFails(getDoc(doc(alice.firestore(), "_passwordResetChallenges/key")));
    await assertFails(getDoc(doc(alice.firestore(), "_otpRequestLimits/key")));
    const postId = "alice_123e4567-e89b-42d3-a456-426614174000";
    const postPath = `communityPosts/${postId}`;
    const post = {authorUid: "alice", author: "Alice", title: "Chào mọi người",
      category: "STUDY", content: "Tôi có một câu hỏi.", createdAt: 1,
      comments: [], voters: []};
    await assertSucceeds(setDoc(doc(alice.firestore(), postPath), post));
    await assertFails(setDoc(doc(bob.firestore(),
        "communityPosts/bob_123e4567-e89b-42d3-a456-426614174001"),
    {...post, authorUid: "bob"}));
    await assertSucceeds(getDoc(doc(bob.firestore(), postPath)));
    await assertFails(getDoc(doc(unverified.firestore(), postPath)));
    const comment = {id: "comment-1", authorUid: "bob", author: "Bob",
      content: "Tôi muốn biết thêm.", createdAt: 2};
    await assertSucceeds(updateDoc(doc(bob.firestore(), postPath),
        {comments: arrayUnion(comment)}));
    await assertFails(updateDoc(doc(bob.firestore(), postPath),
        {comments: arrayUnion({...comment, id: "comment-2", author: "Alice"})}));
    await assertSucceeds(updateDoc(doc(bob.firestore(), postPath),
        {voters: arrayUnion("bob")}));
    await assertFails(updateDoc(doc(bob.firestore(), postPath),
        {voters: arrayUnion("alice")}));
    await assertFails(deleteDoc(doc(bob.firestore(), postPath)));
    await assertSucceeds(deleteDoc(doc(alice.firestore(), postPath)));
    const bucket = "gs://demo-documind.appspot.com";
    const object = "users/alice/documents/doc1";
    await assertSucceeds(uploadString(ref(alice.storage(bucket), object), "test"));
    await assertFails(uploadString(ref(bob.storage(bucket), object), "test"));
    await assertFails(uploadString(ref(unverified.storage(bucket), object), "test"));
    await assertFails(uploadString(ref(guest.storage(bucket), object), "test"));
    process.stdout.write("Firestore and Storage ownership rules passed.\n");
  } finally {
    await env.cleanup();
  }
}

main().catch((error) => {
  process.stderr.write(String(error.stack || error) + "\n");
  process.exitCode = 1;
});
