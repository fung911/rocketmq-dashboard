# Topic Create or Update – Partition Test Documentation

This document explains the design, motivation, and behavior of the partition tests implemented for  
`TopicServiceImpl.createOrUpdate(TopicConfigInfo)` in RocketMQ Dashboard.

The goal of this document is to clarify:
- the system concepts involved (Cluster, Broker, Topic, Message),
- why mocking is required,
- how the test environment is constructed,
- what boundaries are covered by the partition tests,
- and what each test case is verifying.

---

## 1. RocketMQ System Overview

The RocketMQ system can be understood using the following hierarchy:

```
RocketMQ System
 └── Cluster
     └── Broker
         └── Topic
             └── Message
```

- **Cluster**  
  A logical grouping of RocketMQ brokers, usually representing an environment or deployment unit
  (e.g., test cluster, production cluster).

- **Broker**  
  A RocketMQ server instance that actually stores messages and handles message production and
  consumption.

- **Topic**  
  A logical category of messages. Producers send messages to a topic, and consumers subscribe to a
  topic.

- **Message**  
  The actual data unit transmitted through RocketMQ.

---

## 2. Message Type Concept

In RocketMQ, messages within a topic can have different semantics depending on the message type.

Common message types include:

- **NORMAL**  
  Standard messages without ordering guarantees.

- **FIFO**  
  Messages that require strict ordering (e.g., order processing, transaction workflows).

The `messageType` attribute determines how messages under a topic behave.  
If a user does not explicitly specify a message type, RocketMQ Dashboard should apply a reasonable
default.

**Design rule under test**:
- If `messageType` is blank, null, or whitespace-only, it should default to `NORMAL`.
- If `messageType` is explicitly specified, it must be preserved.

---

## 3. What is MQAdminExt?

`MQAdminExt` is an **official RocketMQ Admin Client API** provided by RocketMQ.

It allows RocketMQ Dashboard to perform administrative operations, such as:

- Examining cluster topology (clusters, brokers, addresses)
- Creating or updating topic configurations
- Querying broker status and metadata

In production, `TopicServiceImpl.createOrUpdate` uses `MQAdminExt` to communicate with RocketMQ
brokers.

---

## 4. Why Mocking is Required

In unit testing, we do **not** want to:
- connect to a real RocketMQ cluster,
- depend on external network resources,
- or modify real broker configurations.

Therefore, `MQAdminExt` and related objects are **mocked** using Mockito.

Mocking allows us to:
- control the cluster and broker topology,
- simulate different environments and failure cases,
- verify how many times administrative APIs are called,
- inspect the arguments passed to those calls.

---

## 5. Test Execution Flow

Each test follows the same high-level flow:

1. **Prepare the environment (Arrange)**  
   Mock cluster and broker information using fixed, deterministic data.

2. **Execute the feature under test (Act)**  
   Call the method under test exactly once:
   ```java
   topicService.createOrUpdate(req);
   ```

3. **Verify behavior and outcomes (Assert)**  
   - Verify how many times admin APIs were invoked
   - Verify whether exceptions were thrown
   - Verify that the generated `TopicConfig` is correct

---

## 6. Mocked Environment Setup

The following stubbing defines the mock RocketMQ environment:

```java
when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
when(clusterInfo.getClusterAddrTable()).thenReturn(clusterAddrTable);
when(clusterInfo.getBrokerAddrTable()).thenReturn(brokerAddrTable);
```

This means:

- When `createOrUpdate` requests cluster information,
  it receives a predefined `clusterInfo` object.
- When it queries cluster-to-broker mappings, it receives a fixed map.
- When it queries broker name to broker address mappings, it receives a fixed map.

These mocked responses simulate a RocketMQ cluster without requiring a real deployment.

---

## 7. Behavior of createOrUpdate

The method `createOrUpdate` is invoked **once per test**.

Internally, it performs the following steps:

1. Retrieve cluster and broker metadata using `MQAdminExt`.
2. Resolve the target broker set based on:
   - `clusterNameList`
   - `brokerNameList`
3. For each resolved broker, invoke:
   ```java
   mqAdminExt.createAndUpdateTopicConfig(brokerAddr, topicConfig);
   ```

As a result:
- If there are `k` target brokers, the admin API should be called exactly `k` times.
- The method does **not** return a value; correctness is validated via side effects and interactions.

---

## 8. Partition Test Cases and Boundaries

The input domain is partitioned across three dimensions:

### D1: Message Type
- **M1**: Blank / null / whitespace → default to `NORMAL`
- **M2**: Explicit value (e.g., `FIFO`) → preserved

### D2: Target Broker Resolution
- **B1**: `clusterNameList` only
- **B2**: `brokerNameList` only
- **B3**: Both provided (union, deduplicated)
- **B4**: Neither provided (empty targets)

### D3: Invalid Environment Mappings
- **E1**: Unknown cluster name
- **E2**: Broker missing in broker address table

---

## 9. Summary of Test Cases

| Test Case | Covered Partitions | Boundary Focus | Expected Outcome |
|----------|-------------------|---------------|------------------|
| Case 1 | M1 + B1 | messageType whitespace defaulting | Admin API called 2 times; messageType = NORMAL |
| Case 2 | M2 + B2 | messageType preservation | Admin API called 2 times; messageType = FIFO |
| Case 3 | B3 | Union and deduplication of brokers | Admin API called 3 times |
| Case 4 | B4 | Empty target set | Admin API called 0 times |
| Case 5 | E1 | Unknown cluster mapping | RuntimeException thrown |
| Case 6 | E2 | Missing broker address | RuntimeException thrown |

---

## 10. What This Test Verifies

This partition test verifies that:

- Default values are applied correctly.
- Target brokers are resolved correctly under all input combinations.
- Duplicate broker targets are not processed multiple times.
- Invalid configurations fail fast with exceptions.
- Administrative APIs are invoked the correct number of times with correct parameters.

Together, these tests provide confidence that `createOrUpdate` behaves correctly across its entire
input domain.

---

## 11. Conclusion

By applying equivalence partitioning and boundary value analysis, this test suite validates both
normal and exceptional behaviors of topic creation and update logic in RocketMQ Dashboard. The
tests focus on observable side effects and interactions with external dependencies, ensuring robust
and predictable behavior in real deployment environments.
