# Security policy

Subscription Master handles Google OAuth tokens and data read from users' Gmail, so security reports are taken seriously.

## Reporting a vulnerability

**Do not open a public issue, discussion or pull request for a vulnerability.**

Report it privately through GitHub: open the repository's **Security** tab and click **Report a vulnerability**. Include:

- what is affected (endpoint, file, or flow),
- steps to reproduce or a proof of concept,
- the impact you expect (for example, reading another user's data).

You should get a first reply within 7 days. Please give a reasonable time to ship a fix before disclosing anything publicly. Reporters are credited in the advisory unless they prefer otherwise.

## Scope

In scope: this repository and the official instance at `subscriptionmaster.app`, for example:

- authentication, JWT and refresh-token handling,
- Google OAuth flow and stored Gmail tokens,
- access to another user's subscriptions, emails or attachments,
- account and Gmail data deletion.

Out of scope: denial-of-service and volume testing against the official instance, social engineering, and self-hosted instances run by other people (report those to their operators).

Only test with accounts you own. Do not access, change or delete other people's data.

## Supported versions

Only the latest commit on `main` is supported.
