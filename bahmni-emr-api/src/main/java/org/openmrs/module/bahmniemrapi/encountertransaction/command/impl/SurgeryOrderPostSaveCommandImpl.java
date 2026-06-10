package org.openmrs.module.bahmniemrapi.encountertransaction.command.impl;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Provider;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.bahmniemrapi.encountertransaction.command.EncounterDataPostSaveCommand;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.operationtheater.api.model.SurgicalAppointment;
import org.openmrs.module.operationtheater.api.service.SurgicalAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class SurgeryOrderPostSaveCommandImpl implements EncounterDataPostSaveCommand {

    static final String SELECT_SURGERY_CONCEPT_NAME = "Select Surgery";
    static final String SURGERY_ORDER_TYPE_NAME = "Surgery Order";
    static final String GENERAL_ORDER_TYPE_NAME = "General Order";

    private final OrderService orderService;
    private final ConceptService conceptService;
    private final ProviderService providerService;
    private final SessionFactory sessionFactory;

    @Autowired
    public SurgeryOrderPostSaveCommandImpl(OrderService orderService, ConceptService conceptService,
            ProviderService providerService, SessionFactory sessionFactory) {
        this.orderService = orderService;
        this.conceptService = conceptService;
        this.providerService = providerService;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public EncounterTransaction save(BahmniEncounterTransaction bahmniEncounterTransaction, Encounter currentEncounter,
            EncounterTransaction updatedEncounterTransaction) {

        String surgicalApptUuid = findSurgicalAppointmentUuid(bahmniEncounterTransaction.getObservations());
        String requiredOrderType = surgicalApptUuid != null ? SURGERY_ORDER_TYPE_NAME : GENERAL_ORDER_TYPE_NAME;

        Order existingOrder = findOrderByType(currentEncounter, requiredOrderType);

        if (existingOrder != null) {
            linkUnlinkedObsToOrder(existingOrder, currentEncounter);
            return updatedEncounterTransaction;
        }

        Order newOrder = createOrder(requiredOrderType, currentEncounter);
        if (newOrder == null) {
            return updatedEncounterTransaction;
        }

        linkAllObsToOrder(newOrder, currentEncounter);
        linkSurgicalAppointmentToOrder(surgicalApptUuid, newOrder);

        return updatedEncounterTransaction;
    }

    private String findSurgicalAppointmentUuid(Collection<BahmniObservation> observations) {
        if (observations == null) {
            return null;
        }
        for (BahmniObservation obs : observations) {
            if (isSelectSurgeryObs(obs)) {
                return (String) obs.getValue();
            }
            String found = findSurgicalAppointmentUuid(obs.getGroupMembers());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isSelectSurgeryObs(BahmniObservation obs) {
        return obs.getConcept() != null
                && SELECT_SURGERY_CONCEPT_NAME.equals(obs.getConcept().getName())
                && obs.getValue() instanceof String
                && StringUtils.isNotBlank((String) obs.getValue());
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

        Order order = new Order();
        order.setPatient(encounter.getPatient());
        order.setEncounter(encounter);
        order.setOrderType(orderType);
        order.setConcept(conceptService.getConceptByName(SELECT_SURGERY_CONCEPT_NAME));
        order.setCareSetting(orderService.getCareSettingByName("OUTPATIENT"));
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

    // HQL bulk update bypasses ImmutableEntityInterceptor (same pattern as BahmniObsDaoImpl.updateObsMember).
    private void linkAllObsToOrder(Order order, Encounter encounter) {
        sessionFactory.getCurrentSession()
                .createQuery("UPDATE Obs o SET o.order = :order WHERE o.encounter = :encounter AND o.voided = false")
                .setParameter("order", order)
                .setParameter("encounter", encounter)
                .executeUpdate();
    }

    private void linkUnlinkedObsToOrder(Order order, Encounter encounter) {
        sessionFactory.getCurrentSession()
                .createQuery("UPDATE Obs o SET o.order = :order WHERE o.encounter = :encounter AND o.voided = false AND o.order IS NULL")
                .setParameter("order", order)
                .setParameter("encounter", encounter)
                .executeUpdate();
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
