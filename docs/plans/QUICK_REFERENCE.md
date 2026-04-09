# Surgery Order Implementation - Quick Reference

## Request Format

```bash
curl -X POST http://localhost:8080/rest/v1/bahmnicore/bahmniencounter \
  -H "Content-Type: application/json" \
  -d '{
    "patientUuid": "uuid",
    "encounterTypeUuid": "uuid",
    "visitTypeUuid": "uuid",
    "locationUuid": "uuid",
    "surgeryId": "SURG-2024-001",  # OPTIONAL
    "observations": []
  }'
```

## Implementation Checklist

### Phase 1: API Contract (bahmni-emr-api)
- [ ] Add `surgeryId: String` field to BahmniEncounterTransaction
- [ ] Add getter/setter methods
- [ ] No validation needed

### Phase 2: Surgery Order Module (New OMOD)
- [ ] Create bahmni-surgery-order-omod module structure
- [ ] Create SurgeryOrder entity with JPA annotations
- [ ] Create SurgeryOrderService interface (4 methods)
- [ ] Create SurgeryOrderServiceImpl (implement 4 methods)
- [ ] Create SurgeryOrderDAO interface
- [ ] Create SurgeryOrderDAOImpl
- [ ] Create sqldiff.xml for surgery_order table creation

### Phase 3: Post-Save Command (bahmni-emr-api)
- [ ] Create CreateSurgeryOrderCommand class
- [ ] Implement EncounterDataPostSaveCommand interface
- [ ] Add surgeryOrderService dependency injection
- [ ] Implement save() method with conditional logic
- [ ] Implement addSurgeryObservation() helper method
- [ ] Add Spring @Component annotation

### Phase 4: Integration
- [ ] Add surgeryOrderId/Uuid to response mapper (optional)
- [ ] Configure bahmni.surgery.concept.uuid global property
- [ ] Test with and without surgeryId
- [ ] Verify database tables populated

## Code Snippets

### Add to BahmniEncounterTransaction
```java
private String surgeryId;

public String getSurgeryId() {
    return surgeryId;
}

public void setSurgeryId(String surgeryId) {
    this.surgeryId = surgeryId;
}
```

### SurgeryOrder Entity Skeleton
```java
@Entity
@Table(name = "surgery_order")
public class SurgeryOrder extends BaseOpenmrsData {
    @Id
    @GeneratedValue
    private Integer id;

    @Column
    private String uuid;

    @ManyToOne
    private Visit visit;

    @ManyToOne
    private Encounter encounter;

    @Column
    private String surgeryId;

    // getters/setters
}
```

### CreateSurgeryOrderCommand Skeleton
```java
@Component
public class CreateSurgeryOrderCommand implements EncounterDataPostSaveCommand {

    @Autowired
    private SurgeryOrderService surgeryOrderService;

    @Override
    public EncounterTransaction save(
            BahmniEncounterTransaction bet,
            Encounter encounter,
            EncounterTransaction eTx) {

        if (StringUtils.isNotBlank(bet.getSurgeryId())) {
            // Create surgery order
            // Add observation
        }

        return eTx;
    }
}
```

## Database Schema (sqldiff.xml snippet)
```xml
<createTable tableName="surgery_order">
    <column name="id" type="int" autoIncrement="true">
        <constraints primaryKey="true"/>
    </column>
    <column name="uuid" type="varchar(38)">
        <constraints nullable="false" unique="true"/>
    </column>
    <column name="visit_id" type="int">
        <constraints nullable="false"/>
    </column>
    <column name="encounter_id" type="int">
        <constraints nullable="false"/>
    </column>
    <column name="surgery_id" type="varchar(255)">
        <constraints nullable="false"/>
    </column>
</createTable>
```

## Testing Quick Checklist

```bash
# Test with surgeryId
curl -X POST .../bahmniencounter \
  -d '{"patientUuid":"...", "surgeryId":"SURG-001", ...}'
# Expected: surgery_order table entry + obs entry

# Test without surgeryId
curl -X POST .../bahmniencounter \
  -d '{"patientUuid":"...", ...}'
# Expected: normal encounter creation, no surgery_order entry

# Verify database
SELECT * FROM surgery_order WHERE encounter_id = XXX;
SELECT * FROM obs WHERE encounter_id = XXX AND concept_id = SURGERY_CONCEPT;
```

## Service Methods to Implement

```java
// 1. Create surgery order
createSurgeryOrder(visitId, encounterId, surgeryId) → SurgeryOrder

// 2. Get by encounter
getSurgeryOrderByEncounterId(encounterId) → SurgeryOrder

// 3. Get by surgery ID
getSurgeryOrderBySurgeryId(surgeryId) → SurgeryOrder

// 4. Update surgery order
updateSurgeryOrder(surgeryOrder) → SurgeryOrder
```

## Files to Modify/Create

| File | Action | Type |
|------|--------|------|
| BahmniEncounterTransaction.java | Modify | Update API |
| SurgeryOrder.java | Create | New Entity |
| SurgeryOrderService.java | Create | Interface |
| SurgeryOrderServiceImpl.java | Create | Implementation |
| SurgeryOrderDAO.java | Create | Interface |
| SurgeryOrderDAOImpl.java | Create | Implementation |
| CreateSurgeryOrderCommand.java | Create | Post-Save Hook |
| sqldiff.xml | Create | Database |

## Common Errors & Solutions

| Error | Cause | Solution |
|-------|-------|----------|
| surgeryId not passed through | Not added to transaction | Add field + getter/setter |
| Command not executing | Not registered as @Component | Add @Component annotation |
| Obs not created | Concept UUID not found | Configure global property |
| Foreign key error | Visit/Encounter IDs invalid | Validate IDs before saving |

## Performance Considerations

- Add indexes on: encounter_id, visit_id, surgery_id
- Keep surgeryId as VARCHAR (not UUID) for business identifier
- Use lazy loading for Visit/Encounter relationships

## Backward Compatibility

✓ surgeryId is optional - existing code unchanged
✓ No breaking changes to API
✓ Non-blocking error handling - encounter succeeds even if surgery order fails
