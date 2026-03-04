# UserServiceImpl Testing – Stub vs Mock

## Overview

This assignment demonstrates two common **unit testing techniques** used in Java:

- **Stub-based testing**
- **Mock-based testing**

Both techniques allow us to test a class **in isolation** without depending on external systems such as databases, authentication storage, or RocketMQ resources.

The tests focus on the class:

```
org.apache.rocketmq.dashboard.service.impl.UserServiceImpl
```

Two separate test classes are implemented:

```
UserServiceImplStubTest
UserServiceImplMockTest
```

---

# Stub vs Mock

## Stub

A **stub** replaces part of the system with a simplified implementation that returns **controlled values**.

Key idea:

> We override a method to control what it returns so that the rest of the logic can be tested independently.

Characteristics:

- Focus on **return values**
- Does **not verify interactions**
- Usually implemented by **subclassing or simple fake objects**

In this assignment, a stub is used to override:

```
queryByName(...)
```

so that `queryByUsernameAndPassword(...)` can be tested without touching the real `UserContext` or authentication storage.

---

## Mock

A **mock** is a test double that records how it is used.  
Mocks allow tests to verify that certain **methods were called with specific arguments**.

Key idea:

> Verify the **interaction between objects**.

Characteristics:

- Focus on **method calls**
- Can verify:
  - method was called
  - how many times
  - arguments passed
- Implemented using a framework such as **Mockito**

In this assignment, mocks are used to simulate:

```
UserMQAdminPoolManager
```

so we can verify that `UserServiceImpl` correctly interacts with the MQ admin pool.

---

# How to Run the Tests

Run the tests using Maven with Java 17:

```
JAVA_HOME=/usr/local/opt/openjdk@17 \
mvn test -Dtest=UserServiceImplStubTest,UserServiceImplMockTest
```

This command executes both test classes.

---

## Stub Tests – `UserServiceImplStubTest`

These tests verify the logic of:

```
queryByUsernameAndPassword(String username, String password)
```

A manual stub is used to override `queryByName(...)` so the test can control the returned user.

| Test Method | Scenario | Source Logic Verified |
|-------------|----------|-----------------------|
| `testQueryByUsernameAndPassword_PasswordMismatch_ReturnsNull` | User exists but password is incorrect | `if (!user.getPassword().equals(password)) return null` |
| `testQueryByUsernameAndPassword_PasswordMatch_ReturnsUser` | User exists and password matches | Method returns the user |
| `testQueryByUsernameAndPassword_UserNotFound_ReturnsNull` | `queryByName(...)` returns `null` | `if (user == null) return null` |

---

## Mock Tests – `UserServiceImplMockTest`

These tests verify how `UserServiceImpl` interacts with:

```
UserMQAdminPoolManager
```

Mockito mocks are used to ensure the correct pool operations are called.

| Test Method | Scenario | Source Logic Verified |
|-------------|----------|-----------------------|
| `testGetMQAdminExtForUser_BorrowsFromPool` | Valid user requests MQAdminExt | `borrowMQAdminExt(username, password)` is called |
| `testGetMQAdminExtForUser_NullUser_Throws` | User is `null` | `IllegalArgumentException` is thrown |
| `testReturnMQAdminExtForUser_ReturnsToPool` | Returning admin instance | `returnMQAdminExt(username, admin)` is called |
| `testReturnMQAdminExtForUser_NullArgs_NoPoolInteraction` | Invalid arguments | No interaction with pool manager |
| `testOnUserLogout_ShutsDownUserPool` | User logs out | `shutdownUserPool(username)` is called |

---

These approaches allow the `UserServiceImpl` class to be tested **without starting Spring, without connecting to RocketMQ, and without relying on external systems**, making the tests fast and deterministic.