package org.javers.core.diff.appenders

import org.javers.core.diff.AbstractDiffTest
import org.javers.core.diff.ChangeAssert
import org.javers.core.diff.NodePair
import org.javers.core.model.DummyUser
import org.javers.model.mapping.Property
import org.javers.model.object.graph.ObjectNode
import org.joda.time.LocalDateTime
import spock.lang.Unroll
import static org.javers.core.diff.ChangeAssert.*

import static org.javers.test.builder.DummyUserBuilder.dummyUser

/**
 * @author bartosz walacik
 */
class MapChangeAppenderTest extends AbstractDiffTest{

    @Unroll
    def "should not append mapChanges when maps are #what" () {
        given:
        ObjectNode left =  buildGraph(dummyUser("1").withPrimitiveMap(leftMap).build())
        ObjectNode right = buildGraph(dummyUser("1").withPrimitiveMap(rightMap).build())
        Property valueMap = getEntity(DummyUser).getProperty("primitiveMap")

        expect:
        def changes = new MapChangeAppender().calculateChanges(new NodePair(left,right),valueMap)
        changes.size() == 0

        where:
        what << ["equal","null"]
        leftMap <<  [["some":1], null]
        rightMap << [["some":1], null]
    }

    def "should set MapChange metadata"() {
        given:
        ObjectNode left =  buildGraph(dummyUser("1").withPrimitiveMap(null).build())
        ObjectNode right = buildGraph(dummyUser("1").withPrimitiveMap(["some":1]).build())
        Property primitiveMap = getEntity(DummyUser).getProperty("primitiveMap")

        when:
        def changes =  new MapChangeAppender().calculateChanges(new NodePair(left,right),primitiveMap)

        then:
        assertThat(changes[0])
                    .hasProperty(primitiveMap)
                    .hasInstanceId(DummyUser, "1")
                    .hasAffectedCdo(right)
    }

    @Unroll
    def "should append #changeType when left map is #leftMap and rightMap is #rightMap"() {
        given:
        ObjectNode left =  buildGraph(dummyUser("1").withPrimitiveMap(leftMap).build())
        ObjectNode right = buildGraph(dummyUser("1").withPrimitiveMap(rightMap).build())
        Property primitiveMap = getEntity(DummyUser).getProperty("primitiveMap")

        expect:
        def changes = new MapChangeAppender().calculateChanges(new NodePair(left,right),primitiveMap)
        changes.size() == 1
        changes[0].entry.key == "some"
        changes[0].entry.value == 1
        changes[0].class.simpleName == changeType

        where:
        changeType << ["EntryAdded","EntryRemoved","EntryAdded",         "EntryRemoved"]
        leftMap <<    [null,        ["some":1],    ["other":1],          ["some":1,"other":1] ]
        rightMap <<   [["some":1],   null,         ["other":1,"some":1], ["other":1] ]
    }

    def "should append EntryChanged when Primitive entry.value is changed"() {
        given:
        ObjectNode left =  buildGraph(dummyUser("1").withPrimitiveMap(["some":1,"other":2] ).build())
        ObjectNode right = buildGraph(dummyUser("1").withPrimitiveMap(["some":2,"other":2]).build())
        Property primitiveMap = getEntity(DummyUser).getProperty("primitiveMap")

        when:
        def changes =  new MapChangeAppender().calculateChanges(new NodePair(left,right),primitiveMap)

        then:
        changes.size() == 1
        changes[0].key == "some"
        changes[0].leftValue == 1
        changes[0].rightValue == 2
    }

    def "should append EntryChanged when ValueType entry.value is changed"() {

        def dayOne = new LocalDateTime(2000,1,1,12,1)
        def dayTwo = new LocalDateTime(2000,1,1,12,2)

        given:
        ObjectNode left =  buildGraph(dummyUser("1")
                          .withValueMap(["some":dayOne, "other":dayTwo] ).build())
        ObjectNode right = buildGraph(dummyUser("1")
                          .withValueMap(["some":dayTwo, "other":dayTwo]).build())
        Property valueMap = getEntity(DummyUser).getProperty("valueMap")

        when:
        def changes =  new MapChangeAppender().calculateChanges(new NodePair(left,right),valueMap)

        then:
        changes.size() == 1
        changes[0].key == "some"
        changes[0].leftValue ==   dayOne
        changes[0].rightValue ==  dayTwo
    }
}
