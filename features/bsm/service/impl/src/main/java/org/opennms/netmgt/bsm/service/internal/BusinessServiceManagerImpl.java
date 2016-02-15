/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.bsm.service.internal;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.core.criteria.Criteria;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceChildEdgeEntity;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceDao;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceEdgeDao;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceEdgeEntity;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceEntity;
import org.opennms.netmgt.bsm.persistence.api.IPServiceEdgeEntity;
import org.opennms.netmgt.bsm.persistence.api.SingleReductionKeyEdgeEntity;
import org.opennms.netmgt.bsm.persistence.api.functions.reduce.ReductionFunctionDao;
import org.opennms.netmgt.bsm.service.BusinessServiceManager;
import org.opennms.netmgt.bsm.service.BusinessServiceSearchCriteria;
import org.opennms.netmgt.bsm.service.BusinessServiceStateMachine;
import org.opennms.netmgt.bsm.service.internal.edge.AbstractEdge;
import org.opennms.netmgt.bsm.service.internal.edge.ChildEdgeImpl;
import org.opennms.netmgt.bsm.service.internal.edge.IpServiceEdgeImpl;
import org.opennms.netmgt.bsm.service.internal.edge.ReductionKeyEdgeImpl;
import org.opennms.netmgt.bsm.service.model.BusinessService;
import org.opennms.netmgt.bsm.service.model.BusinessServiceHierarchy;
import org.opennms.netmgt.bsm.service.model.IpService;
import org.opennms.netmgt.bsm.service.model.Node;
import org.opennms.netmgt.bsm.service.model.Status;
import org.opennms.netmgt.bsm.service.model.edge.ChildEdge;
import org.opennms.netmgt.bsm.service.model.edge.Edge;
import org.opennms.netmgt.bsm.service.model.edge.IpServiceEdge;
import org.opennms.netmgt.bsm.service.model.edge.ReductionKeyEdge;
import org.opennms.netmgt.bsm.service.model.functions.map.Decrease;
import org.opennms.netmgt.bsm.service.model.functions.map.Identity;
import org.opennms.netmgt.bsm.service.model.functions.map.Ignore;
import org.opennms.netmgt.bsm.service.model.functions.map.Increase;
import org.opennms.netmgt.bsm.service.model.functions.map.SetTo;
import org.opennms.netmgt.bsm.service.model.functions.reduce.HighestSeverityAbove;
import org.opennms.netmgt.bsm.service.model.functions.reduce.MostCritical;
import org.opennms.netmgt.bsm.service.model.functions.reduce.Threshold;
import org.opennms.netmgt.bsm.service.model.functions.map.MapFunction;
import org.opennms.netmgt.bsm.service.model.functions.reduce.ReductionFunction;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.events.EventBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

@Transactional
public class BusinessServiceManagerImpl implements BusinessServiceManager {
    @Autowired
    private BusinessServiceDao businessServiceDao;

    @Autowired
    private ReductionFunctionDao reductionFunctionDao;

    @Autowired
    private BusinessServiceEdgeDao edgeDao;

    @Autowired
    private MonitoredServiceDao monitoredServiceDao;

    @Autowired
    private BusinessServiceStateMachine businessServiceStateMachine;

    @Autowired
    private NodeDao nodeDao;

    @Autowired
    private EventForwarder eventForwarder;

    @Override
    public List<BusinessService> getAllBusinessServices() {
        return getDao().findAll().stream()
                .map(s -> new BusinessServiceImpl(this, s))
                .collect(Collectors.toList());
    }

    @Override
    public List<BusinessService> search(BusinessServiceSearchCriteria businessServiceSearchCriteria) {
        Objects.requireNonNull(businessServiceSearchCriteria);
        return businessServiceSearchCriteria.apply(this, getAllBusinessServices());
    }

    @Override
    public List<BusinessService> findMatching(Criteria criteria) {
        criteria = transform(criteria);
        List<BusinessServiceEntity> all = getDao().findMatching(criteria);
        if (all == null) {
            return null;
        }
        return all.stream().map(e -> new BusinessServiceImpl(this, e)).collect(Collectors.toList());
    }

    @Override
    public int countMatching(Criteria criteria) {
        criteria = transform(criteria);
                return getDao().countMatching(criteria);
            }

            @Override
            public BusinessService createBusinessService() {
        return new BusinessServiceImpl(this, new BusinessServiceEntity());
    }

    private <T extends Edge> T createEdge(Class<T> type, BusinessService source, MapFunction mapFunction, int weight) {
        T edge = null;
        if (type == IpServiceEdge.class) {
            edge = (T) new IpServiceEdgeImpl(this, new IPServiceEdgeEntity());
        }
        if (type == ChildEdge.class) {
            edge = (T) new ChildEdgeImpl(this, new BusinessServiceChildEdgeEntity());
        }
        if (type == ReductionKeyEdge.class) {
            edge = (T) new ReductionKeyEdgeImpl(this, new SingleReductionKeyEdgeEntity());
        }
        if (edge != null) {
            edge.setSource(source);
            edge.setMapFunction(mapFunction);
            edge.setWeight(weight);
            return edge;
        }
        throw new IllegalArgumentException("Could not create edge for type " + type);
    }

    @Override
    public Edge getEdgeById(Long edgeId) {
        BusinessServiceEdgeEntity edgeEntity = getBusinessServiceEdgeEntity(edgeId);
        if (edgeEntity instanceof BusinessServiceChildEdgeEntity) {
            return new ChildEdgeImpl(this, (BusinessServiceChildEdgeEntity) edgeEntity);
        }
        if (edgeEntity instanceof SingleReductionKeyEdgeEntity) {
            return new ReductionKeyEdgeImpl(this, (SingleReductionKeyEdgeEntity) edgeEntity);
        }
        if (edgeEntity instanceof IPServiceEdgeEntity) {
            return new IpServiceEdgeImpl(this, (IPServiceEdgeEntity) edgeEntity);
        }
        throw new IllegalArgumentException("Could not create edge for entity " + edgeEntity.getClass());
    }

    @Override
    public boolean deleteEdge(BusinessService source, Edge edge) {
        BusinessServiceEdgeEntity edgeEntity = getBusinessServiceEdgeEntity(edge);
        BusinessServiceEntity businessServiceEntity = getBusinessServiceEntity(source);

        // does not exist, no update necessary
        if (!businessServiceEntity.getEdges().contains(edgeEntity)) {
            return false;
        }

        // remove and update
        businessServiceEntity.removeEdge(edgeEntity);
        return true;
    }

    @Override
    public void saveBusinessService(BusinessService service) {
        BusinessServiceEntity entity = getBusinessServiceEntity(service);
        getDao().saveOrUpdate(entity);
    }

    @Override
    public Set<BusinessService> getParentServices(Long id) {
        BusinessServiceEntity entity = getBusinessServiceEntity(id);
        return businessServiceDao.findParents(entity)
            .stream()
            .map(bs -> new BusinessServiceImpl(this, bs))
            .collect(Collectors.toSet());
    }

    @Override
    public BusinessService getBusinessServiceById(Long id) {
        BusinessServiceEntity entity = getBusinessServiceEntity(id);
        return new BusinessServiceImpl(this, entity);
    }

    @Override
    public void deleteBusinessService(BusinessService businessService) {
        BusinessServiceEntity entity = getBusinessServiceEntity(businessService);
        // remove all parent -> child associations
        for(BusinessServiceEntity parent : getDao().findParents(entity)) {
            List<BusinessServiceChildEdgeEntity> collect = parent.getChildEdges().stream().filter(e -> entity.equals(e.getChild())).collect(Collectors.toList());
            collect.forEach(x -> {
                parent.removeEdge(x);
                edgeDao.delete(x); // we need to delete this edge manually as they cannot be deleted automatically
            });
        }
        // edges of the entity are deleted automatically by hibernate
        getDao().delete(entity);
    }

    @Override
    public void setReductionKeyEdges(BusinessService businessService, Set<ReductionKeyEdge> reductionKeyEdges) {
        final BusinessServiceEntity parentEntity = getBusinessServiceEntity(businessService);
        for (final SingleReductionKeyEdgeEntity e : parentEntity.getReductionKeyEdges()) {
            parentEntity.removeEdge(e);
        }
        reductionKeyEdges.forEach(e -> parentEntity.addEdge(((ReductionKeyEdgeImpl) e).getEntity()));
    }

    @Override
    public BusinessServiceHierarchy getHierarchy() {
        return new BusinessServiceHierarchyImpl(getAllBusinessServices());
    }

    @Override
    public boolean addReductionKeyEdge(BusinessService businessService, String reductionKey, MapFunction mapFunction, int weight) {
        final BusinessServiceEntity parentEntity = getBusinessServiceEntity(businessService);

        // Create the edge
        final ReductionKeyEdgeImpl edge = (ReductionKeyEdgeImpl) createEdge(ReductionKeyEdge.class, businessService, mapFunction, weight);
        edge.setReductionKey(reductionKey);

        // if already exists, no update
        final SingleReductionKeyEdgeEntity edgeEntity = getBusinessServiceEdgeEntity(edge);
        long count = parentEntity.getReductionKeyEdges().stream().filter(e -> e.equalsDefinition(edgeEntity)).count();
        if (count > 0) {
            return false;
        }
        parentEntity.addEdge(edge.getEntity());
        return true;
    }

    @Override
    public void setIpServiceEdges(BusinessService businessService, Set<IpServiceEdge> ipServiceEdges) {
        final BusinessServiceEntity entity = getBusinessServiceEntity(businessService);
        for (final IPServiceEdgeEntity e : entity.getIpServiceEdges()) {
            entity.removeEdge(e);
        }
        ipServiceEdges.forEach(e -> entity.addEdge(((IpServiceEdgeImpl) e).getEntity()));
    }

    @Override
    public boolean addIpServiceEdge(BusinessService businessService, IpService ipService, MapFunction mapFunction, int weight) {
        final BusinessServiceEntity parentEntity = getBusinessServiceEntity(businessService);

        // Create the edge
        final IpServiceEdge edge = createEdge(IpServiceEdge.class, businessService, mapFunction, weight);
        edge.setIpService(ipService);

        // if already exists, no update
        final IPServiceEdgeEntity edgeEntity = getBusinessServiceEdgeEntity(edge);
        long count = parentEntity.getIpServiceEdges().stream().filter(e -> e.equalsDefinition(edgeEntity)).count();
        if (count > 0) {
            return false;
        }
        parentEntity.addEdge(((IpServiceEdgeImpl)edge).getEntity());
        return true;
    }

    @Override
    public void setChildEdges(BusinessService parentService, Set<ChildEdge> childEdges) {
        final BusinessServiceEntity parentEntity = getBusinessServiceEntity(parentService);
        for (final BusinessServiceChildEdgeEntity e : parentEntity.getChildEdges()) {
            parentEntity.removeEdge(e);
        }
        childEdges.forEach(e -> parentEntity.addEdge(((ChildEdgeImpl) e).getEntity()));
    }

    @Override
    public boolean addChildEdge(BusinessService parentService, BusinessService childService, MapFunction mapFunction, int weight) {
        // verify that exists
        final BusinessServiceEntity parentEntity = getBusinessServiceEntity(parentService);
        final BusinessServiceEntity childEntity = getBusinessServiceEntity(childService);

        // Create the edge
        ChildEdge childEdge = createEdge(ChildEdge.class, parentService, mapFunction, weight);
        childEdge.setChild(childService);

        // Verify no loop
        if (this.checkDescendantForLoop(parentEntity, childEntity)) {
            throw new IllegalArgumentException("Service will form a loop");
        }
        // if already exists, no update
        final BusinessServiceChildEdgeEntity edgeEntity = getBusinessServiceEdgeEntity(childEdge);
        long count = parentEntity.getChildEdges().stream().filter(e -> e.equalsDefinition(edgeEntity)).count();
        if (count > 0) {
            return false;
        }
        parentEntity.addEdge(((ChildEdgeImpl)childEdge).getEntity());
        return true;
    }

    private boolean checkDescendantForLoop(final BusinessServiceEntity parent,
                                           final BusinessServiceEntity descendant) {
        if (parent.equals(descendant)) {
            return true;
        }

        for (BusinessServiceChildEdgeEntity eachChildEdge : descendant.getChildEdges()) {
            return this.checkDescendantForLoop(parent, eachChildEdge.getChild());
        }

        return false;
    }

    @Override
    public Set<BusinessService> getFeasibleChildServices(final BusinessService service) {
        final BusinessServiceEntity entity = getBusinessServiceEntity(service);
        return getDao().findAll()
                       .stream()
                       .filter(s -> !this.checkDescendantForLoop(entity, s))
                       .map(s -> new BusinessServiceImpl(this, s))
                       .collect(Collectors.<BusinessService>toSet());
    }

    @Override
    public Status getOperationalStatusForBusinessService(BusinessService service) {
        final Status status = businessServiceStateMachine.getOperationalStatus(service);
        return status != null ? status : Status.INDETERMINATE;
    }

    @Override
    public Status getOperationalStatusForIPService(IpService ipService) {
        final Status status = businessServiceStateMachine.getOperationalStatus(ipService);
        return status != null ? status : Status.INDETERMINATE;
    }

    @Override
    public List<IpService> getAllIpServices() {
        return monitoredServiceDao.findAll().stream()
                                  .map(s -> new IpServiceImpl(this, s))
                                  .collect(Collectors.toList());
    }

    @Override
    public IpService getIpServiceById(Integer id) {
        OnmsMonitoredService entity = getMonitoredServiceEntity(id);
        return new IpServiceImpl(this, entity);
    }

    @Override
    public void triggerDaemonReload() {
        EventBuilder eventBuilder = new EventBuilder(EventConstants.RELOAD_DAEMON_CONFIG_UEI, "BSM Master Page");
        eventBuilder.addParam(EventConstants.PARM_DAEMON_NAME, "bsmd");
        eventForwarder.sendNow(eventBuilder.getEvent());
    }

    @Override
    public Status getOperationalStatusForReductionKey(String reductionKey) {
        final Status status = businessServiceStateMachine.getOperationalStatus(reductionKey);
        return status != null ? status : Status.INDETERMINATE;
    }

    @Override
    public Status getOperationalStatusForEdge(Edge edge) {
        Objects.requireNonNull(edge);
        if (edge instanceof ReductionKeyEdge) {
            return getOperationalStatusForReductionKey(((ReductionKeyEdge) edge).getReductionKey());
        }
        if (edge instanceof ChildEdge) {
            return getOperationalStatusForBusinessService(((ChildEdge) edge).getChild());
        }
        if (edge instanceof IpServiceEdge) {
            return getOperationalStatusForIPService(((IpServiceEdge) edge).getIpService());
        }
        throw new IllegalArgumentException("Could not determine status for edge of type " + edge.getClass());
    }

    @Override
    public List<MapFunction> listMapFunctions() {
        return Lists.newArrayList(new Identity(), new Increase(), new Decrease(), new SetTo(), new Ignore());
    }

    @Override
    public List<ReductionFunction> listReduceFunctions() {
        return Lists.newArrayList(new MostCritical(), new Threshold(), new HighestSeverityAbove());
    }

    @Override
    public Node getNodeById(Integer nodeId) {
        return new NodeImpl(this, getNodeEntity(nodeId));
    }

    protected BusinessServiceDao getDao() {
        return this.businessServiceDao;
    }

    private OnmsNode getNodeEntity(Integer nodeId) {
        Objects.requireNonNull(nodeId);
        final OnmsNode entity = nodeDao.get(nodeId);
        if (entity == null) {
            throw new NoSuchElementException();
        }
        return entity;
    }

    private BusinessServiceEdgeEntity getBusinessServiceEdgeEntity(Edge edge) {
        return ((AbstractEdge) edge).getEntity();
    }

    private BusinessServiceEdgeEntity getBusinessServiceEdgeEntity(Long edgeId) {
        Objects.requireNonNull(edgeId);
        BusinessServiceEdgeEntity edgeEntity = edgeDao.get(edgeId);
        if (edgeEntity == null) {
            throw new NoSuchElementException();
        }
        return edgeEntity;
    }

    private IPServiceEdgeEntity getBusinessServiceEdgeEntity(IpServiceEdge ipServiceEdge) {
        return ((IpServiceEdgeImpl) ipServiceEdge).getEntity();
    }

    private BusinessServiceChildEdgeEntity getBusinessServiceEdgeEntity(ChildEdge childEdge) {
        return ((ChildEdgeImpl) childEdge).getEntity();
    }

    private SingleReductionKeyEdgeEntity getBusinessServiceEdgeEntity(ReductionKeyEdge reductionKeyEdge) {
        return ((ReductionKeyEdgeImpl) reductionKeyEdge).getEntity();
    }

    private BusinessServiceEntity getBusinessServiceEntity(BusinessService service) throws NoSuchElementException {
        return ((BusinessServiceImpl) service).getEntity();
    }

    private BusinessServiceEntity getBusinessServiceEntity(Long serviceId) throws NoSuchElementException {
        Objects.requireNonNull(serviceId);
        final BusinessServiceEntity entity = getDao().get(serviceId);
        if (entity == null) {
            throw new NoSuchElementException();
        }
        return entity;
    }

    private OnmsMonitoredService getMonitoredServiceEntity(IpService ipService) throws NoSuchElementException {
        return ((IpServiceImpl) ipService).getEntity();
    }

    private OnmsMonitoredService getMonitoredServiceEntity(Integer serviceId) throws NoSuchElementException {
        Objects.requireNonNull(serviceId);
        final OnmsMonitoredService monitoredService = monitoredServiceDao.get(serviceId);
        if (monitoredService == null) {
            throw new NoSuchElementException();
        }
        return monitoredService;
    }

    /**
     * The criteria is build on BusinessService classes.
     * However we want to use the dao to filter. Therefore we have to perform a mapping from BusinessService to BusinessServiceEntity.
     *
     * @param input
     * @return
     */
    private Criteria transform(Criteria input) {
        Criteria criteria = input.clone();
        criteria.setClass(BusinessServiceEntity.class);
        return criteria;
    }
}