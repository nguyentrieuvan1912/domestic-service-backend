# Backend collaboration rules

Read README.md and docs/TEAM_ASSIGNMENT.md before changing backend code.

- Java 21; Maven Wrapper at repository root. Spring Boot and Cloud versions belong to root pom.xml.
- Record/reference IDs use Long/BIGINT. Primary IDs auto-increment per table; demo IDs are 1, 2, 3... . Inherited Admin/Customer/Staff share users.id. Opaque tokens such as hold_token are not entity IDs.
- Person 1 owns identity-service, catalog-service, finance-service, api-gateway and shared infrastructure.
- Person 2 owns booking-service and ai-assistant-service.
- common-web contains HTTP concerns only; never share JPA entities or domain repositories.
- Each service owns one database and its Flyway migrations. No cross-database SQL, JPA relations or shared entity imports.
- Never edit an applied migration. Add the next version inside the owning service.
- Routes use /api/v1/{identity|catalog|booking|finance|ai-assistant}/...
- Update docs/ only for changes that affect both developers: shared setup, API contracts consumed by the other service, schema/enum changes affecting integration, ownership and handoff instructions.
- For Person 1-only implementation details, debugging notes and manual run/test instructions, use .local-notes/person1/. This directory is ignored by Git and Docker; do not stage or force-add it.
- Private service work does not require shared docs updates unless it changes a shared interface or setup. Keep source code and required migrations under their owning service as usual.
- When private work is completed, leave reproducible manual steps, example requests/SQL, expected results and actual verification status in .local-notes/person1/.
- Before suggesting a commit, inspect the diff and include only relevant shared docs. Do not commit/push automatically merely because documentation is eligible for the repository.
- Cross-service changes require agreement with the other owner.
- Keep credentials in ignored .env; examples contain local-development values only.
- Do not change root pom.xml, compose.yml, common-web or gateway for service-only work unless required and coordinated.
- Run ./mvnw verify (Windows: .\mvnw.cmd verify) before handing off changes.
- Do not commit target directories, editor config, .env or generated files.
- Use feature branches and PRs. Do not push directly to main or force-push shared branches.
