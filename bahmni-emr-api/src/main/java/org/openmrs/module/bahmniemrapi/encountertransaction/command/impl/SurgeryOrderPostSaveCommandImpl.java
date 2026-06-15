package org.openmrs.module.bahmniemrapi.encountertransaction.command.impl;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.bahmniemrapi.dao.SurgeryObsOrderLinkDao;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Provider;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.bahmniemrapi.encountertransaction.command.EncounterDataPostSaveCommand;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.operationtheater.api.model.SurgicalAppointment;
import org.openmrs.module.operationtheater.api.service.SurgicalAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SurgeryOrderPostSaveCommandImpl implements EncounterDataPostSaveCommand {

    static final String SELECT_SURGERY_CONCEPT_NAME = "Select Surgery";
    static final String SURGERY_ORDER_TYPE_NAME = "Surgery Order";
    static final String GENERAL_ORDER_TYPE_NAME = "General Order";

    private final OrderService orderService;
    private final ConceptService conceptService;
    private final SurgeryObsOrderLinkDao surgeryObsOrderLinkDao;

    @Autowired
    public SurgeryOrderPostSaveCommandImpl(OrderService orderService, ConceptService conceptService,
            SurgeryObsOrderLinkDao surgeryObsOrderLinkDao) {
        this.orderService = orderService;
        this.conceptService = conceptService;
        this.surgeryObsOrderLinkDao = surgeryObsOrderLinkDao;
    }

    @Override
    public EncounterTransaction save(BahmniEncounterTransaction bahmniEncounterTransaction, Encounter currentEncounter,
            EncounterTransaction updatedEncounterTransaction) {
        String surgicalApptUuid = findSurgicalAppointmentUuidFromNewObs(currentEncounter);

        Order existingOrder = surgicalApptUuid != null
                ? findExistingOrderForSurgicalAppointment(surgicalApptUuid)
                : findOrderByType(currentEncounter, GENERAL_ORDER_TYPE_NAME);

        if (existingOrder != null) {
            surgeryObsOrderLinkDao.assignOrderToUnlinkedObs(existingOrder, currentEncounter);
            return updatedEncounterTransaction;
        }

        String requiredOrderType = surgicalApptUuid != null ? SURGERY_ORDER_TYPE_NAME : GENERAL_ORDER_TYPE_NAME;
        Order newOrder = createOrder(requiredOrderType, currentEncounter);
        if (newOrder == null) {
            return updatedEncounterTransaction;
        }

        surgeryObsOrderLinkDao.assignOrderToUnlinkedObs(newOrder, currentEncounter);
        linkSurgicalAppointmentToOrder(surgicalApptUuid, newOrder);

        return updatedEncounterTransaction;
    }

    private String findSurgicalAppointmentUuidFromNewObs(Encounter encounter) {
        for (org.openmrs.Obs obs : encounter.getObs()) {
            if (!obs.getVoided() && obs.getOrder() == null
                    && obs.getConcept() != null
                    && obs.getConcept().getName() != null
                    && SELECT_SURGERY_CONCEPT_NAME.equals(obs.getConcept().getName().getName())
                    && StringUtils.isNotBlank(obs.getValueComplex())) {
                return obs.getValueComplex();
            }
        }
        return null;
    }

    private Order findExistingOrderForSurgicalAppointment(String surgicalApptUuid) {
        SurgicalAppointmentService svc = Context.getService(SurgicalAppointmentService.class);
        SurgicalAppointment appt = svc.getSurgicalAppointmentByUuid(surgicalApptUuid);
        if (appt != null && appt.getOrder() != null && !appt.getOrder().getVoided()) {
            return appt.getOrder();
        }
        return null;
    }

    private Order findOrderByType(Encounter encounter, String orderTypeName) {
        for (Order order : encounter.getOrders()) {
            if (!order.getVoided() && order.getOrderType() != null
                    && orderTypeName.equals(order.getOrderType().getName())) {
                return order;
            }
        }
        return null;
    }

    private Order createOrder(String orderTypeName, Encounter encounter) {
        OrderType orderType = orderService.getOrderTypeByName(orderTypeName);
        if (orderType == null) {
            return null;
        }

        Provider provider = getPrimaryProvider(encounter);
        if (provider == null) {
            return null;
        }

        // Fix 1: null guard on concept
        Concept concept = conceptService.getConceptByName(SELECT_SURGERY_CONCEPT_NAME);
        if (concept == null) {
            return null;
        }

        // Fix 3: null guard on careSetting + use enum constant
        CareSetting careSetting = orderService.getCareSettingByName(
                CareSetting.CareSettingType.OUTPATIENT.toString());
        if (careSetting == null) {
            return null;
        }

        Order order = new Order();
        order.setPatient(encounter.getPatient());
        order.setEncounter(encounter);
        order.setOrderType(orderType);
        order.setConcept(concept);
        order.setCareSetting(careSetting);
        order.setOrderer(provider);
        order.setDateActivated(encounter.getEncounterDatetime());

        return orderService.saveOrder(order, null);
    }

    private Provider getPrimaryProvider(Encounter encounter) {
        for (EncounterProvider ep : encounter.getEncounterProviders()) {
            if (ep.getProvider() != null) {
                return ep.getProvider();
            }
        }
        return null;
    }

    private void linkSurgicalAppointmentToOrder(String surgicalApptUuid, Order order) {
        if (surgicalApptUuid == null) {
            return;
        }
        SurgicalAppointmentService svc = Context.getService(SurgicalAppointmentService.class);
        SurgicalAppointment appt = svc.getSurgicalAppointmentByUuid(surgicalApptUuid);
        if (appt != null) {
            appt.setOrder(order);
            svc.save(appt);
        }
    }
}
