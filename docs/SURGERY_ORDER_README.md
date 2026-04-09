# Surgery Order Feature - Complete Documentation

## Overview
This documentation covers the complete implementation plan for adding Surgery Order functionality to Bahmni Core. The feature allows optional surgery order creation when posting encounters with a `surgeryId`.

## Documentation Files

### 1. Sequence Diagrams

#### Current Workflow (Baseline)
- **File**: `sequences/post-bahmniencounter-flow.mmd` (Mermaid)
- **File**: `sequences/post-bahmniencounter-flow.puml` (PlantUML)
- **Description**: Shows the existing POST /bahmnicore/bahmniencounter workflow without surgery orders

#### Enhanced Workflow (With Surgery Orders)
- **File**: `sequences/post-bahmniencounter-with-surgery-order.mmd` (Mermaid)
- **File**: `sequences/post-bahmniencounter-with-surgery-order.puml` (PlantUML)
- **Description**: Shows the complete flow including optional surgery order creation

### 2. Implementation Plan
- **File**: `plans/SURGERY_ORDER_IMPLEMENTATION.md`
- **Description**: Detailed implementation guide covering:
  - Phase 1: API Contract Update (BahmniEncounterTransaction)
  - Phase 2: Create Surgery Order Module (OMOD)
  - Phase 3: Create Post-Save Command
  - Phase 4: Integration Steps
  - Configuration & Testing Strategy

## Key Architectural Components

### 1. API Layer (bahmni-emr-api)
- **Update**: BahmniEncounterTransaction
  - Add optional `surgeryId` field (String)
  - Add getter and setter methods
  - No validation required

### 2. Business Logic Layer (bahmni-emr-api)
- **New**: CreateSurgeryOrderCommand (EncounterDataPostSaveCommand)
  - Implements post-save hook
  - Creates surgery order if surgeryId provided
  - Adds observation entry linking encounter to surgery
  - Gracefully handles errors (non-blocking)

### 3. Surgery Order Module (New OMOD: bahmni-surgery-order-omod)

#### Domain Model
- **SurgeryOrder** Entity
  - Stores: visit_id, encounter_id, surgery_id, order_date
  - Auto-generates UUID
  - Tracks creation metadata

#### Service Layer
- **SurgeryOrderService** Interface
  - createSurgeryOrder(visitId, encounterId, surgeryId)
  - getSurgeryOrderByEncounterId(encounterId)
  - updateSurgeryOrder(surgeryOrder)
  - getSurgeryOrderBySurgeryId(surgeryId)

- **SurgeryOrderServiceImpl** Implementation
  - Transactional operations
  - Input validation
  - Data persistence via DAO

#### Data Access Layer
- **SurgeryOrderDAO** Interface
  - save(SurgeryOrder)
  - getByEncounterId(Integer)
  - getBySurgeryId(String)
  - update(SurgeryOrder)

### 4. Database Schema
**New Table**: `surgery_order`
```sql
CREATE TABLE surgery_order (
    id INT PRIMARY KEY AUTO_INCREMENT,
    uuid VARCHAR(38) UNIQUE NOT NULL,
    visit_id INT NOT NULL,
    encounter_id INT NOT NULL,
    surgery_id VARCHAR(255) NOT NULL,
    order_date DATETIME,
    date_created DATETIME NOT NULL,
    creator INT NOT NULL,
    uuid_changed DATETIME NOT NULL,
    FOREIGN KEY (visit_id) REFERENCES visit(visit_id),
    FOREIGN KEY (encounter_id) REFERENCES encounter(encounter_id),
    FOREIGN KEY (creator) REFERENCES users(user_id),
    INDEX idx_so_encounter_id (encounter_id),
    INDEX idx_so_visit_id (visit_id),
    INDEX idx_so_surgery_id (surgery_id)
);
```

**Modified Table**: `obs`
```
- New entry created with:
  - encounter_id (from created encounter)
  - concept_id (Surgery concept)
  - value_text (surgeryId)
  - obs_order_id (links to surgery_order.id)
```

## Request/Response Examples

### Request (With Optional Surgery ID)
```json
POST /rest/v1/bahmnicore/bahmniencounter
{
  "patientUuid": "patient-uuid-123",
  "encounterTypeUuid": "encounter-type-uuid",
  "visitTypeUuid": "visit-type-uuid",
  "locationUuid": "location-uuid",
  "encounterDateTime": "2024-04-09T10:00:00Z",
  "surgeryId": "SURG-2024-001",
  "observations": [
    {
      "conceptUuid": "concept-uuid",
      "value": "some-value"
    }
  ]
}
```

### Response
```json
{
  "encounterUuid": "encounter-uuid-456",
  "visitUuid": "visit-uuid-789",
  "patientUuid": "patient-uuid-123",
  "surgeryId": "SURG-2024-001",
  "context": {
    "surgeryOrderId": 123,
    "surgeryOrderUuid": "order-uuid-111"
  },
  "observations": [...]
}
```

## Implementation Flow

```
1. Client sends POST /bahmnicore/bahmniencounter with optional surgeryId
   ↓
2. BahmniEncounterController receives request
   ↓
3. BahmniEncounterTransactionService.save() executes
   ↓
4. Encounter is created and saved to database
   ↓
5. PostSaveCommands execute (including CreateSurgeryOrderCommand)
   ↓
6. If surgeryId is present:
   - Get visit from encounter
   - Call SurgeryOrderService.createSurgeryOrder()
   - Surgery order saved to surgery_order table
   - Observation entry created linking to surgery
   - Order ID stored in transaction context
   ↓
7. If surgeryId is NOT present:
   - Skip surgery order creation
   - Continue normal flow
   ↓
8. Response returned with surgery details (if created)
```

## Key Features

### Optional Implementation
- surgeryId is **optional** - existing code unchanged
- No validation required
- Backward compatible

### Error Handling
- Surgery order creation failures logged but non-blocking
- Encounter creation succeeds even if surgery order fails
- Graceful degradation

### Extensibility
- Post-save command hook allows future enhancements
- Service interface supports additional operations
- DAO can be extended for custom queries

## Configuration Requirements

### Global Properties
```
bahmni.surgery.concept.uuid = [UUID of Surgery concept in OpenMRS]
```

### Module Dependencies
- OpenMRS Platform (core)
- bahmni-emr-api
- reference-data

## Testing Checklist

### Unit Tests
- [ ] BahmniEncounterTransaction surgeryId getter/setter
- [ ] SurgeryOrderServiceImpl.createSurgeryOrder()
- [ ] SurgeryOrderServiceImpl.getSurgeryOrderByEncounterId()
- [ ] CreateSurgeryOrderCommand surgery order creation
- [ ] CreateSurgeryOrderCommand obs entry creation

### Integration Tests
- [ ] POST with surgeryId → surgery order created
- [ ] POST without surgeryId → normal flow
- [ ] POST with invalid surgeryId → handled gracefully
- [ ] Surgery order linked to encounter and visit
- [ ] Obs entry correctly populated
- [ ] Response includes surgery order details

### End-to-End Tests
- [ ] API response contains surgeryOrderId
- [ ] Database tables populated correctly
- [ ] Indexes working for performance
- [ ] Backward compatibility maintained

## Deployment Steps

1. **Create bahmni-surgery-order-omod module**
   - Define structure per Spring Boot/OpenMRS conventions
   - Implement SurgeryOrder, DAO, Service

2. **Run database migration**
   - Execute sqldiff.xml to create surgery_order table

3. **Update bahmni-emr-api**
   - Add surgeryId field to BahmniEncounterTransaction
   - Implement CreateSurgeryOrderCommand

4. **Configure global properties**
   - Set bahmni.surgery.concept.uuid

5. **Test thoroughly**
   - Run unit, integration, and end-to-end tests

6. **Deploy to environments**
   - Dev → QA → Staging → Production

## File Structure

```
docs/
├── SURGERY_ORDER_README.md (this file)
├── plans/
│   └── SURGERY_ORDER_IMPLEMENTATION.md
└── sequences/
    ├── post-bahmniencounter-flow.mmd
    ├── post-bahmniencounter-flow.puml
    ├── post-bahmniencounter-with-surgery-order.mmd
    └── post-bahmniencounter-with-surgery-order.puml
```

## How to View Diagrams

### PlantUML Diagrams
1. Online: [PlantUML Online Editor](http://www.plantuml.com/plantuml/uml/)
2. Copy content from `.puml` files and paste

### Mermaid Diagrams
1. GitHub: Renders directly in `.mmd` files
2. Online: [Mermaid Live Editor](https://mermaid.live)
3. Copy content from `.mmd` files and paste

## References
- [OpenMRS Documentation](https://wiki.openmrs.org)
- [Bahmni Documentation](https://bahmni.org/documentation)
- [PlantUML Guide](https://plantuml.com/guide)
- [Mermaid Guide](https://mermaid.js.org)

## Questions or Issues?
Refer to the detailed implementation plan for:
- Architecture decisions
- Code examples
- Configuration details
- Testing strategies
- Deployment considerations
