package org.javers.core;

import org.javers.common.exception.JaversException;
import org.javers.common.validation.Validate;
import org.javers.core.changelog.ChangeListTraverser;
import org.javers.core.changelog.ChangeProcessor;
import org.javers.core.commit.Commit;
import org.javers.core.commit.CommitFactory;
import org.javers.core.diff.Change;
import org.javers.core.diff.Diff;
import org.javers.core.diff.DiffFactory;
import org.javers.core.diff.changetype.PropertyChange;
import org.javers.core.json.JsonConverter;
import org.javers.core.metamodel.annotation.TypeName;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.core.metamodel.object.GlobalIdFactory;
import org.javers.core.metamodel.property.Property;
import org.javers.core.metamodel.type.*;
import org.javers.repository.api.JaversExtendedRepository;
import org.javers.repository.jql.GlobalIdDTO;
import org.javers.repository.jql.JqlQuery;
import org.javers.repository.jql.QueryRunner;
import org.javers.shadow.Shadow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.javers.common.exception.JaversExceptionCode.COMMITTING_TOP_LEVEL_VALUES_NOT_SUPPORTED;
import static org.javers.common.validation.Validate.argumentsAreNotNull;
import static org.javers.repository.jql.InstanceIdDTO.instanceId;

/**
 * JaVers instance
 *
 * @author bartosz walacik
 */
class JaversCore implements Javers {
    private static final Logger logger = LoggerFactory.getLogger(Javers.class);

    private final DiffFactory diffFactory;
    private final TypeMapper typeMapper;
    private final JsonConverter jsonConverter;
    private final CommitFactory commitFactory;
    private final JaversExtendedRepository repository;
    private final QueryRunner queryRunner;
    private final GlobalIdFactory globalIdFactory;
    private final JaversCoreConfiguration configuration;

    JaversCore(DiffFactory diffFactory, TypeMapper typeMapper, JsonConverter jsonConverter, CommitFactory commitFactory, JaversExtendedRepository repository, QueryRunner queryRunner, GlobalIdFactory globalIdFactory, JaversCoreConfiguration javersCoreConfiguration) {
        this.diffFactory = diffFactory;
        this.typeMapper = typeMapper;
        this.jsonConverter = jsonConverter;
        this.commitFactory = commitFactory;
        this.repository = repository;
        this.queryRunner = queryRunner;
        this.globalIdFactory = globalIdFactory;
        this.configuration = javersCoreConfiguration;
    }

    @Override
    public Commit commit(String author, Object currentVersion) {
        return commit(author, currentVersion, Collections.<String, String>emptyMap());
    }

    @Override
    public Commit commit(String author, Object currentVersion, Map<String, String> commitProperties) {
        long start = System.currentTimeMillis();

        argumentsAreNotNull(author, commitProperties, currentVersion);

        JaversType jType = typeMapper.getJaversType(currentVersion.getClass());
        if (jType instanceof ValueType || jType instanceof PrimitiveType){
            throw new JaversException(COMMITTING_TOP_LEVEL_VALUES_NOT_SUPPORTED,
                jType.getClass().getSimpleName(), currentVersion.getClass().getSimpleName());
        }

        Commit commit = commitFactory.create(author, commitProperties, currentVersion);
        long stop_f = System.currentTimeMillis();

        if (commit.getSnapshots().isEmpty()) {
            logger.info("Skipping persisting empty commit: {}", commit.toString());
            return commit;
        }

        repository.persist(commit);
        long stop = System.currentTimeMillis();

        logger.info(commit.toString()+", done in "+ (stop-start)+ " millis (diff:{}, persist:{})",(stop_f-start), (stop-stop_f));
        return commit;
    }

    @Override
    public Commit commitShallowDelete(String author, Object deleted) {
        return commitShallowDelete(author, deleted, Collections.<String, String>emptyMap());
    }

    @Override
    public Commit commitShallowDelete(String author, Object deleted, Map<String, String> properties) {
        argumentsAreNotNull(author, properties, deleted);

        Commit commit = commitFactory.createTerminal(author, properties, deleted);

        repository.persist(commit);
        logger.info(commit.toString());
        return commit;
    }

    @Override
    public Commit commitShallowDeleteById(String author, GlobalIdDTO globalId) {
        return  commitShallowDeleteById(author, globalId, Collections.<String, String>emptyMap());
    }


    @Override
    public Commit commitShallowDeleteById(String author, GlobalIdDTO globalId, Map<String, String> properties) {
        argumentsAreNotNull(author, properties, globalId);

        Commit commit = commitFactory.createTerminalByGlobalId(author, properties, globalIdFactory.createFromDto(globalId));

        repository.persist(commit);
        logger.info(commit.toString());
        return commit;
    }

    @Override
    public Diff compare(Object oldVersion, Object currentVersion) {
        argumentsAreNotNull(oldVersion, currentVersion);

        return diffFactory.compare(oldVersion, currentVersion);
    }

    @Override
    public Diff initial(Object newDomainObject) {
        return diffFactory.initial(newDomainObject);
    }

    @Override
    public <T> List<Shadow<T>> findShadows(JqlQuery query) {
        return (List)queryRunner.queryForShadows(query);
    }

    @Override
    public <T> Stream<Shadow<T>> findShadowsAndStream(JqlQuery query) {
        return (Stream)queryRunner.queryForShadowsStream(query);
    }

    @Override
    public List<CdoSnapshot> findSnapshots(JqlQuery query){
        return queryRunner.queryForSnapshots(query);
    }

    @Override
    public Changes findChanges(JqlQuery query){
        return new Changes(queryRunner.queryForChanges(query), configuration.getPrettyValuePrinter());
    }

    @Override
    public Optional<CdoSnapshot> getLatestSnapshot(Object localId, Class entity) {
        Validate.argumentsAreNotNull(localId, entity);
        return queryRunner.runQueryForLatestSnapshot(instanceId(localId, entity));
    }

    @Override
    public Optional<CdoSnapshot> getHistoricalSnapshot(Object localId, Class entity, LocalDateTime effectiveDate) {
        Validate.argumentsAreNotNull(localId, entity, effectiveDate);
        return repository.getHistorical(globalIdFactory.createInstanceId(localId, entity), effectiveDate);
    }

    @Override
    public JsonConverter getJsonConverter() {
        return jsonConverter;
    }

    @Override
    public <T> T processChangeList(List<Change> changes, ChangeProcessor<T> changeProcessor){
        argumentsAreNotNull(changes, changeProcessor);

        ChangeListTraverser.traverse(changes, changeProcessor);
        return changeProcessor.result();
    }

    @Override
    public <T extends JaversType> T getTypeMapping(Type clientsType) {
        return (T) typeMapper.getJaversType(clientsType);
    }

    /**
     * @see TypeName
     * @since 2.3
     */
    public <T extends ManagedType> T getTypeMapping(String typeName) {
        return (T) typeMapper.getJaversManagedType(typeName);
    }

    @Override
    public <T> Diff compareCollections(Collection<T> oldVersion, Collection<T> currentVersion, Class<T> itemClass) {
        return diffFactory.compareCollections(oldVersion, currentVersion, itemClass);
    }

    @Override
    public Property getProperty(PropertyChange propertyChange) {
        ManagedType managedType = typeMapper.getJaversManagedType(propertyChange.getAffectedGlobalId());
        return managedType.getProperty(propertyChange.getPropertyName());
    }
}
