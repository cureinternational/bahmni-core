package org.openmrs.module.bahmniemrapi.encountertransaction.mapper;

import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.VisitType;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.module.bahmniemrapi.accessionnote.mapper.AccessionNotesMapper;
import org.openmrs.module.bahmniemrapi.diagnosis.contract.BahmniDiagnosisRequest;
import org.openmrs.module.bahmniemrapi.diagnosis.helper.BahmniDiagnosisMetadata;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.bahmniemrapi.encountertransaction.mapper.parameters.AdditionalBahmniObservationFields;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BahmniEncounterTransactionMapper {
    private AccessionNotesMapper accessionNotesMapper;
    private BahmniDiagnosisMetadata bahmniDiagnosisMetadata;
    private ObsRelationshipMapper obsRelationshipMapper;
    private PatientService patientService;
    private EncounterService encounterService;
    private VisitService visitService;
    private ETObsToBahmniObsMapper fromETObsToBahmniObs;

    @Autowired
    public BahmniEncounterTransactionMapper(AccessionNotesMapper accessionNotesMapper,
                                            BahmniDiagnosisMetadata bahmniDiagnosisMetadata,
                                            ObsRelationshipMapper obsRelationshipMapper,
                                            PatientService patientService,
                                            EncounterService encounterService,
                                            VisitService visitService,
                                            ETObsToBahmniObsMapper fromETObsToBahmniObs) {
        this.accessionNotesMapper = accessionNotesMapper;
        this.bahmniDiagnosisMetadata = bahmniDiagnosisMetadata;
        this.obsRelationshipMapper = obsRelationshipMapper;
        this.patientService = patientService;
        this.encounterService = encounterService;
        this.visitService = visitService;
        this.fromETObsToBahmniObs = fromETObsToBahmniObs;
    }

    public BahmniEncounterTransaction map(EncounterTransaction encounterTransaction, boolean includeAll) {
        BahmniEncounterTransaction bahmniEncounterTransaction = new BahmniEncounterTransaction(encounterTransaction);
        List<BahmniDiagnosisRequest> bahmniDiagnoses = bahmniDiagnosisMetadata.map(encounterTransaction.getDiagnoses(), includeAll);
        bahmniEncounterTransaction.setBahmniDiagnoses(bahmniDiagnoses);
        bahmniEncounterTransaction.setAccessionNotes(accessionNotesMapper.map(encounterTransaction));
        AdditionalBahmniObservationFields additionalBahmniObservationFields = new AdditionalBahmniObservationFields(encounterTransaction.getEncounterUuid(), encounterTransaction.getEncounterDateTime(), null,null);
        additionalBahmniObservationFields.setProviders(encounterTransaction.getProviders());
        List<BahmniObservation> bahmniObservations = fromETObsToBahmniObs.create(encounterTransaction.getObservations(), additionalBahmniObservationFields);
        bahmniEncounterTransaction.setObservations(obsRelationshipMapper.map(bahmniObservations, encounterTransaction.getEncounterUuid()));
        addPatientIdentifier(bahmniEncounterTransaction, encounterTransaction);
        addEncounterType(encounterTransaction, bahmniEncounterTransaction);
        addVisitType(encounterTransaction, bahmniEncounterTransaction);
        return bahmniEncounterTransaction;
    }

    private void addEncounterType(EncounterTransaction encounterTransaction, BahmniEncounterTransaction bahmniEncounterTransaction) {
        EncounterType encounterType = encounterService.getEncounterTypeByUuid(encounterTransaction.getEncounterTypeUuid());
        if (encounterType != null) {
            bahmniEncounterTransaction.setEncounterType(encounterType.getName());
        }
    }

    private void addVisitType(EncounterTransaction encounterTransaction, BahmniEncounterTransaction bahmniEncounterTransaction) {
        if (!StringUtils.isBlank(encounterTransaction.getVisitTypeUuid())) {
            VisitType visitType = visitService.getVisitTypeByUuid(encounterTransaction.getVisitTypeUuid());
            if (visitType != null) {
                bahmniEncounterTransaction.setVisitType(visitType.getName());
            }
        }
    }

    private void addPatientIdentifier(BahmniEncounterTransaction bahmniEncounterTransaction, EncounterTransaction encounterTransaction) {
        Patient patient = patientService.getPatientByUuid(encounterTransaction.getPatientUuid());
        if (patient != null) {
            PatientIdentifier patientIdentifier = patient.getPatientIdentifier();
            if(patientIdentifier != null){
                bahmniEncounterTransaction.setPatientId(patientIdentifier.getIdentifier());
            }
        }
    }
}
