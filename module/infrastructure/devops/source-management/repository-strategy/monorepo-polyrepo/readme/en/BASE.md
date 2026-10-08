# Monorepo and Polyrepo: Choosing Repository Boundaries

Organizing projects, modules, and software components in **one repository (monorepo)** or **separate repositories (polyrepo)** is a strategic choice about discoverability, ownership, coordination, and independence. Neither topology is universally better; the coordination burden, access requirements, and tooling capabilities all matter.

**Prerequisites:** familiarity with the concept of a Git repository, projects/modules, shared libraries, and collaborative source changes. Pipeline implementation and microservice architecture knowledge are not required.

**Learning journey:** repository topology and its boundaries → area-based versus repository-based ownership → cross-component change coordination → shared dependencies and compatibility → change coupling versus release independence → access and governance → strategic scale/tooling/build/CI trade-offs → choosing, splitting, or consolidating repositories using a decision matrix and practical evidence.

**Key distinctions:** a repository is not necessarily a service, application, or deployment unit. Monorepos do not inherently impose synchronized releases; polyrepos do not eliminate dependencies or guarantee independent releases. Path-based review ownership is not the same as repository-level read-access isolation.

**Boundary:** this module teaches *why* repository-topology choices matter and their strategic consequences. Git commands belong to the Git module, build systems and CI pipeline setup to their tooling/delivery owners, and service decomposition and deployment to software architecture/delivery. The eight chapters now include substantial explanations, comparative scenarios, decision criteria and observable evidence for source-topology choices.
