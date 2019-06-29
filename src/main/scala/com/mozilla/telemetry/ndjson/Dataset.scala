/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package com.mozilla.telemetry.ndjson

import java.time.{LocalDate, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.{ISO_DATE, ISO_DATE_TIME}
import java.util.Base64

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.json4s.{DefaultFormats, Formats, JValue}
import org.json4s.JsonAST.{JObject, JString, JInt}
import org.json4s.jackson.JsonMethods.parseOpt

import scala.util.{Failure, Success, Try}

class Dataset private (inputPath: String, clauses: Map[String, PartialFunction[String, Boolean]]) {
  def where(attribute: String)(clause: PartialFunction[String, Boolean]): Dataset = {
    if (clauses.contains(attribute)) {
      throw new Exception(s"There should be only one clause for $attribute")
    }

    if (attribute == "submissionDate") {
      throw new Exception("submissionDate can only be specified for Dataset()")
    }

    new Dataset(inputPath, clauses + (attribute -> clause))
  }

  def records()(implicit sc: SparkContext): RDD[Option[JValue]] = {
    // configure sc.textFile(path) to allow *
    sc.hadoopConfiguration.set("mapreduce.input.fileinputformat.input.dir.recursive","true")
    // supply implicit for parseOpt
    implicit val formats: Formats = DefaultFormats
    // read ndjson
    val messages = sc.textFile(inputPath).flatMap(parseOpt(_))
    // filter attributes
    val filtered = clauses.foldLeft(messages)((rdd, attributeClause) => {
      val (attribute, clause) = attributeClause
      rdd.filter(_ \ "attributesMap" \ attribute match {
        case JString(value) => clause.isDefinedAt(value) && clause(value)
        case _ => false
      })
    })
    // deserialize Some(JValue) or None from each payload
    filtered.map(
      message => (message \ "payload").extractOpt[String]
    ).map {
      case Some(base64) => Try(Base64.getDecoder.decode(base64))
      case _ => Failure
    }.map {
      case Success(payload) => parseOpt(payload.toString)
      case _ => None
    // provide doc \ "meta" to better match com.mozilla.telemetry.heka.Message.toJValue
    }.map {
      case Some(doc) =>
        val submissionTimestamp = ZonedDateTime.parse(
          (doc \ "submission_timestamp").extract[String],
          ISO_DATE_TIME
        )
        Some(doc ++ JObject(List(
          ("meta", JObject(List(
            ("submissionDate", JString(submissionTimestamp.format(Dataset.DATE_NO_DASH))),
            ("Timestamp", JInt(
              submissionTimestamp.toEpochSecond * 1e9.toLong + submissionTimestamp.getNano.toLong
            )),
            ("documentId", doc \ "document_id"),
            ("clientId", doc \ "clientId"),
            ("sampleId", doc \ "sample_id"),
            ("appUpdateChannel", doc \ "metadata" \ "uri" \ "app_update_channel"),
            ("normalizedChannel", doc \ "normalized_channel"),
            ("normalizedOSVersion", doc \ "normalized_os_version"),
            ("Date", doc \ "metadata" \ "header" \ "date"),
            ("geoCountry", doc \ "metadata" \ "geo" \ "country"),
            ("geoCity", doc \ "metadata" \ "geo" \ "city"),
            ("geoSubdivision1", doc \ "metadata" \ "geo" \ "subdivision1"),
            ("geoSubdivision2", doc \ "metadata" \ "geo" \ "subdivision2")
          )))
        )))
      case _ => None
    }
  }
}

object Dataset {
  val DATE_NO_DASH: DateTimeFormatter = DateTimeFormatter.ofPattern("YYYYmmdd")

  def apply(dataset: String, submissionDate: Option[String] = None,
            // TODO update this to point at moz-fx-data-prod-data when main pings become available there
            bucket: String = "gs://moz-fx-data-stage-data"): Dataset = {
    // add dashes to submissionDate for backwards compatibility
    val isoSubmissionDate = submissionDate.map(LocalDate.parse(_, DATE_NO_DASH).format(ISO_DATE))

    // look up relevant sink output
    val prefix = dataset match {
      case "telemetry" => "telemetry-decoded_gcs-sink/output"
    }

    // TODO add docType to telemetry-decoded output path
    val inputPath = s"$bucket/$prefix/${isoSubmissionDate.getOrElse("*")}/*/*.ndjson.gz"

    new Dataset(inputPath, Map())
  }

  implicit def datasetToRDD(dataset: Dataset)(implicit sc: SparkContext): RDD[Option[JValue]] = {
    dataset.records()
  }
}
