# Surgery Order Implementation Plan

## Overview
This document outlines the implementation plan for adding Surgery Order functionality to the Bahmni Core system. The surgery order will be created optionally when a POST request to `/bahmnicore/bahmniencounter` includes a `surgeryId`.

## Sequence Diagrams
See the following diagrams for visual reference:
- **PlantUML**: `docs/sequences/post-bahmniencounter-with-surgery-order.puml`
- **Mermaid**: `docs/sequences/post-bahmniencounter-with-surgery-order.mmd`

## Implementation Phases

### Phase 1: API Contract Update (bahmni-emr-api)

#### 1.1 Update BahmniEncounterTransaction
**File**: `bahmni-emr-api/src/main/java/org/openmrs/module/bahmniemrapi/encountertransaction/contract/BahmniEncounterTransaction.java`

```java
// Add field
private String surgeryId;

// Add getter
public String getSurgeryId() {
    return surgeryId;
}

// Add setter
public void setSurgeryId(String surgeryId) {
    this.surgeryId = surgeryId;
}

// Update cloneForPastDrugOrders() if needed
// Update toEncounterTransaction() if needed
```

**Changes**:
- Add `surgeryId` field (String, optional)
- Add getter and setter methods
- NO validation required - field is optional
- No impact on serialization (uses @JsonIgnoreProperties)

---

### Phase 2: Create Surgery Order Module (New OMOD)

#### 2.1 Module Structure
```
bahmni-surgery-order-omod/
├── pom.xml
├── src/main/java/org/openmrs/module/bahmni/surgeryorder/
│   ├── api/
│   │   ├── service/
│   │   │   ├── SurgeryOrderService.java (interface)
│   │   │   └── impl/SurgeryOrderServiceImpl.java
│   │   └── dao/
│   │       ├── SurgeryOrderDAO.java (interface)
│   │       └── impl/SurgeryOrderDAOImpl.java
│   ├── model/SurgeryOrder.java
│   └── ModuleActivator.java
├── src/main/resources/
│   ├── config.xml
│   └── sqldiff.xml
└── src/test/java/
```

#### 2.2 SurgeryOrder Domain Model
**File**: `bahmni-surgery-order-omod/src/main/java/.../model/SurgeryOrder.java`

```java
@Entity
@Table(name = "surgery_order")
public class SurgeryOrder extends BaseOpenmrsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(name = "surgery_id", nullable = false)
    private String surgeryId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "order_date")
    private Date orderDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_created")
    private Date dateCreated;

    // Getters and setters
}
```

#### 2.3 SurgeryOrderService Interface
**File**: `bahmni-surgery-order-omod/src/main/java/.../service/SurgeryOrderService.java`

```java
public interface SurgeryOrderService extends OpenmrsService {

    /**
     * Creates a new surgery order
     * @param visitId Visit ID
     * @param encounterId Encounter ID
     * @param surgeryId Surgery ID (business identifier)
     * @return Created SurgeryOrder with generated ID
     */
    SurgeryOrder createSurgeryOrder(Integer visitId, Integer encounterId, String surgeryId);

    /**
     * Gets surgery order by encounter ID
     * @param encounterId Encounter ID
     * @return SurgeryOrder or null if not found
     */
    SurgeryOrder getSurgeryOrderByEncounterId(Integer encounterId);

    /**
     * Updates surgery order
     * @param surgeryOrder SurgeryOrder to update
     * @return Updated SurgeryOrder
     */
    SurgeryOrder updateSurgeryOrder(SurgeryOrder surgeryOrder);

    /**
     * Gets surgery order by surgery ID
     * @param surgeryId Surgery ID
     * @return SurgeryOrder or null
     */
    SurgeryOrder getSurgeryOrderBySurgeryId(String surgeryId);
}
```

#### 2.4 SurgeryOrderService Implementation
**File**: `bahmni-surgery-order-omod/src/main/java/.../service/impl/SurgeryOrderServiceImpl.java`

```java
@Service
@Transactional
public class SurgeryOrderServiceImpl extends BaseOpenmrsService implements SurgeryOrderService {

    @Autowired
    private SurgeryOrderDAO surgeryOrderDAO;

    @Autowired
    private VisitService visitService;

    @Autowired
    private EncounterService encounterService;

    @Override
    public SurgeryOrder createSurgeryOrder(Integer visitId, Integer encounterId, String surgeryId) {
        Visit visit = visitService.getVisit(visitId);
        Encounter encounter = encounterService.getEncounter(encounterId);

        if (visit == null || encounter == null) {
            throw new IllegalArgumentException("Visit or Encounter not found");
        }

        SurgeryOrder surgeryOrder = new SurgeryOrder();
        surgeryOrder.setUuid(UUID.randomUUID().toString());
        surgeryOrder.setVisit(visit);
        surgeryOrder.setEncounter(encounter);
        surgeryOrder.setSurgeryId(surgeryId);
        surgeryOrder.setOrderDate(new Date());

        return surgeryOrderDAO.save(surgeryOrder);
    }

    @Override
    public SurgeryOrder getSurgeryOrderByEncounterId(Integer encounterId) {
        return surgeryOrderDAO.getByEncounterId(encounterId);
    }

    @Override
    public SurgeryOrder updateSurgeryOrder(SurgeryOrder surgeryOrder) {
        return surgeryOrderDAO.update(surgeryOrder);
    }

    @Override
    public SurgeryOrder getSurgeryOrderBySurgeryId(String surgeryId) {
        return surgeryOrderDAO.getBySurgeryId(surgeryId);
    }
}
```

#### 2.5 SurgeryOrderDAO
**File**: `bahmni-surgery-order-omod/src/main/java/.../dao/SurgeryOrderDAO.java`

```java
public interface SurgeryOrderDAO extends GenericDAO<SurgeryOrder> {
    SurgeryOrder getByEncounterId(Integer encounterId);
    SurgeryOrder getBySurgeryId(String surgeryId);
}
```

#### 2.6 Database Changes (sqldiff.xml)
**File**: `bahmni-surgery-order-omod/src/main/resources/sqldiff.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<sqldiff>
    <changeset id="surgery-order-table-20240409" author="vivek">
        <createTable tableName="surgery_order">
            <column name="id" type="int" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="uuid" type="varchar(38)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="visit_id" type="int">
                <constraints nullable="false" foreignKeyName="fk_so_visit_id"
                             references="visit(visit_id)"/>
            </column>
            <column name="encounter_id" type="int">
                <constraints nullable="false" foreignKeyName="fk_so_encounter_id"
                             references="encounter(encounter_id)"/>
            </column>
            <column name="surgery_id" type="varchar(255)">
                <constraints nullable="false"/>
            </column>
            <column name="order_date" type="datetime">
                <constraints nullable="true"/>
            </column>
            <column name="date_created" type="datetime">
                <constraints nullable="false"/>
            </column>
            <column name="creator" type="int">
                <constraints nullable="false" foreignKeyName="fk_so_creator"
                             references="users(user_id)"/>
            </column>
            <column name="uuid_changed" type="datetime">
                <constraints nullable="false"/>
            </column>
        </createTable>

        <createIndex indexName="idx_so_encounter_id" tableName="surgery_order">
            <column name="encounter_id"/>
        </createIndex>

        <createIndex indexName="idx_so_visit_id" tableName="surgery_order">
            <column name="visit_id"/>
        </createIndex>

        <createIndex indexName="idx_so_surgery_id" tableName="surgery_order">
            <column name="surgery_id"/>
        </createIndex>
    </changeset>
</sqldiff>
```

---

### Phase 3: Create Post-Save Command (bahmni-emr-api)

#### 3.1 CreateSurgeryOrderCommand
**File**: `bahmni-emr-api/src/main/java/.../encountertransaction/command/impl/CreateSurgeryOrderCommand.java`

```java
@Component
public class CreateSurgeryOrderCommand implements EncounterDataPostSaveCommand {

    @Autowired
    private SurgeryOrderService surgeryOrderService;

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private ObsService obsService;

    @Override
    @Transactional
    public EncounterTransaction save(
            BahmniEncounterTransaction bahmniEncounterTransaction,
            Encounter encounter,
            EncounterTransaction updatedEncounterTransaction) {

        String surgeryId = bahmniEncounterTransaction.getSurgeryId();

        // Only process if surgeryId is provided
        if (StringUtils.isNotBlank(surgeryId)) {
            try {
                // Get visit from encounter
                Visit visit = encounter.getVisit();
                if (visit == null) {
                    throw new RuntimeException("No visit associated with encounter");
                }

                // Create surgery order
                SurgeryOrder surgeryOrder = surgeryOrderService.createSurgeryOrder(
                    visit.getVisitId(),
                    encounter.getEncounterId(),
                    surgeryId
                );

                // Add observation with surgeryId and link to order
                addSurgeryObservation(encounter, surgeryId, surgeryOrder.getId());

                // Store orderId in transaction context for response
                bahmniEncounterTransaction.getContext().put("surgeryOrderId", surgeryOrder.getId());
                bahmniEncounterTransaction.getContext().put("surgeryOrderUuid", surgeryOrder.getUuid());

            } catch (Exception e) {
                // Log error but don't fail the entire encounter creation
                // Surgery order creation is optional
                logger.error("Failed to create surgery order for surgeryId: " + surgeryId, e);
            }
        }

        return updatedEncounterTransaction;
    }

    private void addSurgeryObservation(Encounter encounter, String surgeryId, Integer orderId) {
        // Create observation for surgery record
        Obs obs = new Obs();
        obs.setEncounter(encounter);
        obs.setObsDatetime(encounter.getEncounterDatetime());

        // Use Surgery concept - configure this UUID in global property
        String surgeryConcept = Context.getAdministrationService()
                .getGlobalProperty("bahmni.surgery.concept.uuid");
        if (StringUtils.isNotBlank(surgeryConcept)) {
            Concept concept = Context.getConceptService().getConceptByUuid(surgeryConcept);
            obs.setConcept(concept);
        }

        obs.setValueText(surgeryId);
        obs.setCreator(Context.getAuthenticatedUser());
        obs.setDateCreated(new Date());

        // Save observation
        Context.getObsService().saveObs(obs, "Created surgery observation");
    }

    @Override
    public String getCommandName() {
        return "CreateSurgeryOrderCommand";
    }
}
```

---

### Phase 4: Integration Steps

#### 4.1 Register Post-Save Command
The `CreateSurgeryOrderCommand` should be automatically registered as a Spring component if using Spring annotation scanning.

#### 4.2 Update Controller Response
The controller already returns `BahmniEncounterTransaction`, which will now include the context data with surgery order details if created.

#### 4.3 Update Response Mapper
If needed, update `BahmniEncounterTransactionMapper` to include surgery order ID in response:

```java
// In mapper
if (bahmniEncounterTransaction.getContext().containsKey("surgeryOrderId")) {
    response.setSurgeryOrderId((Integer) bahmniEncounterTransaction.getContext().get("surgeryOrderId"));
    response.setSurgeryOrderUuid((String) bahmniEncounterTransaction.getContext().get("surgeryOrderUuid"));
}
```

---

## Configuration

### Global Properties to Add

```
bahmni.surgery.concept.uuid = <UUID of Surgery concept>
```

This UUID should point to a concept that represents Surgery in the OpenMRS system.

---

## Testing Strategy

### Unit Tests
- SurgeryOrderServiceImpl
- CreateSurgeryOrderCommand
- BahmniEncounterTransaction surgeryId getter/setter

### Integration Tests
- POST /bahmnicore/bahmniencounter with surgeryId
- POST /bahmnicore/bahmniencounter without surgeryId
- Verify obs entry created
- Verify surgery order created
- Verify surgery order linked to encounter and visit

### Test Cases
1. **Happy Path**: Request with surgeryId → Surgery order created, obs entry added
2. **Optional Path**: Request without surgeryId → Normal encounter creation, no surgery order
3. **Error Handling**: Invalid visit/encounter → Log error, continue encounter creation
4. **Database**: Verify tables populated correctly

---

## Deployment Considerations

1. **Module Installation Order**:
   - Install bahmni-surgery-order-omod first
   - Then update bahmni-emr-api
   - Then update bahmnicore-omod

2. **Database Migration**:
   - Run sqldiff.xml to create surgery_order table
   - No data migration needed (new feature)

3. **Configuration**:
   - Set bahmni.surgery.concept.uuid global property
   - Ensure Surgery concept exists in OpenMRS

4. **Backward Compatibility**:
   - surgeryId is optional - no breaking changes
   - Existing encounters work unchanged
   - Surgery order creation fails gracefully

---

## Summary of Changes

| Module | File | Change | Type |
|--------|------|--------|------|
| bahmni-emr-api | BahmniEncounterTransaction | Add surgeryId field | Feature |
| bahmni-emr-api | CreateSurgeryOrderCommand | New post-save command | Feature |
| bahmni-surgery-order-omod | SurgeryOrder | New domain model | Feature |
| bahmni-surgery-order-omod | SurgeryOrderService | New service interface | Feature |
| bahmni-surgery-order-omod | SurgeryOrderServiceImpl | Service implementation | Feature |
| bahmni-surgery-order-omod | SurgeryOrderDAO | Data access interface | Feature |
| bahmni-surgery-order-omod | sqldiff.xml | Create surgery_order table | Database |

---

## Sequence Flow Summary

1. **Request**: Client sends POST with optional `surgeryId`
2. **Controller**: Passes to service unchanged
3. **Service**: Saves encounter (existing flow)
4. **Post-Save**: CreateSurgeryOrderCommand runs
5. **Surgery Order Creation**: If surgeryId present, creates order and obs entry
6. **Response**: Returns encounter + optional surgery order details

---

## Next Steps

1. Review and approve this plan
2. Create bahmni-surgery-order-omod module
3. Implement domain model and DAO
4. Implement service layer
5. Create post-save command
6. Write unit and integration tests
7. Deploy and test in environment
