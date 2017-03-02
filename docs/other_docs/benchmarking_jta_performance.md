---
---

Benchmarking JTA performance
----------------------------

### Context

JTA Transactions are an important aspect of enterprise Java applications. Datagrid providers support transactions in various forms, even though there is a tendency to support standard JTA based transactions.

RadarGun supports benchmarking interactions with a data grid when used in a JTA transactional environment.

### Plugin support

In order to be able to benchmark, the `CacheWrapper`'s `startTransaction()` and `endTransaction()` methods need to be implemented by plugin providers.

#### CacheWrapper.startTransaction()

This method starts a transaction on the node. All the `put`, `get` and `empty` operations invoked after this method returns will take place within the scope of the transaction started. The transaction will be completed by invoking `endTransaction()`.

#### CacheWrapper.endTransaction()

Called in conjunction with `startTransaction()` in order to complete a transaction by either committing or rolling it back.  A boolean flag is passed into this method, `true` if the transaction is to be committed, `false` if it is to be aborted.

### Configuring the Stress test to use transactions

In order to execute the operations within a transaction, the following attributes of the [Stress Test]({{page.path_to_root}}measuring_performance/stress_test.html) are relevant: `useTransactions`, `transactionSize` and `commitTransactions`. These are fully described in the [Stress Test]({{page.path_to_root}}measuring_performance/stress_test.html) guide.

If the transactionSize is 1 (default) then a transaction will be created and committed for each operation. You might also want to tune the `writePercentage` attribute, e.g. by setting it to 100 in order to only measure transactional writes.

### Plugin specific configurations

The `infinispan5` plugin supports a transaction attribute: `enlistExtraXAResource`. E.g.

    <products>
      <infinispan5>
         ...
         <config file="dist-tx.xml" cache="xaNoRecovery" name="xa-no-recovery-1pc-off" enlistExtraXAResource="true"/>
         ...
      </infinispan5>
    </products>

When this is configured, the `InfinispanWrapper` registers an additional XAResource whenever it starts a new transaction. This is for enforcing the `TransactionManager` to always write the transaction log to the disk, in order to avoid a last-resource-commit optimization that some transaction managers do.

