# Sequence Diagrams

This directory contains sequence diagrams for the Bahmni Core system, illustrating the flow of various API endpoints and operations.

## POST /bahmnicore/bahmniencounter

### Files
- **post-bahmniencounter-flow.mmd** - Mermaid format sequence diagram
- **post-bahmniencounter-flow.puml** - PlantUML format sequence diagram

### Description
This diagram illustrates the complete flow of the POST /bahmnicore/bahmniencounter endpoint, which creates or updates an encounter in the system.

### Flow Overview

1. **Controller Layer**
   - `BahmniEncounterController.update()` receives the POST request
   - Sets UUIDs for observations

2. **Service Layer**
   - `BahmniEncounterTransactionService.save()` orchestrates the encounter creation
   - Retrieves patient information
   - Sets encounter metadata (datetime, type, visit type)
   - Handles drug orders (including retrospective entries)

3. **Pre-Save Processing**
   - Executes all registered `EncounterDataPreSaveCommand` implementations
   - Handles retrospective encounter entries if applicable
   - Sets visit location information

4. **Persistence Layer**
   - `EmrEncounterService.save()` persists the encounter and observations to the database
   - Retrieves the saved encounter for further processing

5. **Post-Save Processing**
   - Executes all registered `EncounterDataPostSaveCommand` implementations
   - Saves visit attributes
   - Maps the result back to `BahmniEncounterTransaction`

6. **Response**
   - Returns the saved encounter transaction to the client

### Key Components
- **BahmniEncounterController** - REST endpoint handler
- **BahmniEncounterTransactionService** - Business logic orchestrator
- **EmrEncounterService** - EMR API encounter persistence layer
- **EncounterService** - OpenMRS core encounter operations
- **Database** - Persistent data storage

### How to View

#### Mermaid Diagram
- Supported by: GitHub, GitLab, Notion, Obsidian, and many other platforms
- View in GitHub: The `.mmd` file will render directly in GitHub
- View locally: Use [Mermaid Live Editor](https://mermaid.live) and paste the content

#### PlantUML Diagram
- Supported by: Confluence, Jira, and many documentation tools
- View locally: Use [PlantUML Online Editor](http://www.plantuml.com/plantuml/uml/)
- Command line: `plantuml post-bahmniencounter-flow.puml -Tpng`

### Integration with Development

These diagrams help developers understand:
- The sequence of operations when creating/updating encounters
- Where extension points exist (pre/post-save commands)
- Database query patterns and interactions
- Separation of concerns across layers
