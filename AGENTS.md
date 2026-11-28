# Working agreements

- Do not add attack-style code, including in tests or diagnostic scripts. The owner
  explicitly excludes network tests that attempt cross-device publishing,
  wildcard subscriptions, unauthorized retained-message writes, or broker bypasses.
- Use permitted-operation integration tests and policy-only unit tests instead.
  Document verification gaps rather than adding excluded tests.
- Keep Moquette unchanged unless the owner explicitly approves otherwise. Its
  documented limitations are accepted for this private, trusted-network deployment;
  do not claim complete topic isolation or suitability for public exposure.
