# Backend collaboration rules

Read README.md and docs/TEAM_ASSIGNMENT.md before changing backend code.

- Java 21; Maven Wrapper at repository root. Spring Boot and Cloud versions belong to root pom.xml.
- Person 1 owns identity-service, catalog-service, finance-service, api-gateway and shared infrastructure.
- Person 2 owns booking-service and ai-assistant-service.
- common-web contains HTTP concerns only; never share JPA entities or domain repositories.
- Each service owns one database and its Flyway migrations. No cross-database SQL, JPA relations or shared entity imports.
- Never edit an applied migration. Add the next version inside the owning service.
- Routes use /api/v1/{identity|catalog|booking|finance|ai-assistant}/...
- Update the owning contract doc when public API changes. Cross-service changes require agreement with the other owner.
- Keep credentials in ignored .env; examples contain local-development values only.
- Do not change root pom.xml, compose.yml, common-web or gateway for service-only work unless required and coordinated.
- Run ./mvnw verify (Windows: .\mvnw.cmd verify) before handing off changes.
- Do not commit target directories, editor config, .env or generated files.
- Use feature branches and PRs. Do not push directly to main or force-push shared branches.
