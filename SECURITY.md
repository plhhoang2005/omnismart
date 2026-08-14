# Security Policy

## Supported versions

OmniSmart is currently pre-release. Security fixes are applied to the latest code on `main` and the most recent tagged release candidate.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability.

Use GitHub's **Report a vulnerability** feature under the repository Security tab to create a private security advisory. Include:

- affected version or commit;
- reproduction steps;
- expected and observed behavior;
- potential impact;
- suggested mitigation, if known.

Do not include real credentials or personal data in a report. The maintainers will acknowledge a complete report as soon as practical, assess severity and coordinate a fix before public disclosure.

## Security baseline

- Secrets must be provided through approved environment or secret stores.
- OAuth credentials and connector tokens must never be committed.
- Every business query must enforce store/tenant isolation.
- Human approval is required before AI-generated content is published.
- Dependencies and workflows are reviewed and updated regularly.

