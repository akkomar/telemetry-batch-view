/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package com.mozilla.telemetry.spark

import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.sources.{DataSourceRegister, RelationProvider}

/** Register a custom Spark Data Source for faking BigQuery Storage API with Spark temporary views.
  *
  * <p>Requires src/test/resources/META-INF/services/org.apache.spark.sql.sources.DataSourceRegister
  * to contain com.mozilla.telemetry.spark.FakeBigQueryRelation
  */
class FakeBigQueryDataSource extends DataSourceRegister with RelationProvider {
  override def shortName: String = "bigquery"
  override def createRelation(sqlContext: SQLContext, parameters: Map[String, String]): FakeBigQueryRelation =
    new FakeBigQueryRelation(parameters("table"), parameters.get("filter"))(sqlContext)
}
