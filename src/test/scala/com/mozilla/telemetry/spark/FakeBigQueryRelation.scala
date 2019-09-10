/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package com.mozilla.telemetry.spark

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import org.apache.spark.sql.sources.{BaseRelation, TableScan}
import org.apache.spark.sql.types.StructType

/** Implement a custom Spark Data Source for faking BigQuery Storage API with Spark temporary views.
  *
  * <p>Requires src/test/resources/META-INF/services/org.apache.spark.sql.sources.DataSourceRegister
  * to contain com.mozilla.telemetry.spark.FakeBigQueryRelation
  */
class FakeBigQueryRelation(table: String, filter: Option[String])(@transient val sqlContext: SQLContext) extends BaseRelation with TableScan {
  private val df = sqlContext.sql(s"SELECT * FROM $table WHERE ${filter.getOrElse("TRUE")}")
  override def buildScan: RDD[Row] = df.rdd
  override val schema: StructType = df.schema
}
