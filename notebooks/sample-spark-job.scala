// Databricks notebook source
System.setProperty("spline.mode", "REQUIRED")
System.setProperty("spline.persistence.factory", "za.co.absa.spline.persistence.mongo.MongoPersistenceFactory")
System.setProperty("spline.mongodb.url", dbutils.secrets.get("spline", "spline.mongodb.url"))
import za.co.absa.spline.core.SparkLineageInitializer._
spark.enableLineageTracking()

// COMMAND ----------

// MAGIC %python
// MAGIC rawData = spark.read.option("inferSchema", "true").json("/databricks-datasets/structured-streaming/events/")
// MAGIC rawData.createOrReplaceTempView("rawData")
// MAGIC sql("select r1.action, count(*) as actionCount from rawData as r1 join rawData as r2 on r1.action = r2.action group by r1.action").write.mode('overwrite').csv("/tmp/pyaggaction.csv")

// COMMAND ----------


