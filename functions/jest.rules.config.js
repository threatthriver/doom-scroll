// Firestore rules tests. Needs the emulator:
//   firebase emulators:exec --only firestore --project demo-secure-message "npm --prefix functions run test:rules"
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/test-rules"],
  testTimeout: 20000,
};
