/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.carbondata.spark.testsuite.dataload

import org.apache.spark.sql.Row
import org.apache.spark.sql.test.util.QueryTest
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import org.apache.carbondata.common.exceptions.sql.InvalidLoadOptionException
import org.apache.carbondata.core.datamap.Segment
import org.apache.carbondata.core.datastore.impl.FileFactory
import org.apache.carbondata.core.indexstore.blockletindex.SegmentIndexFileStore
import org.apache.carbondata.core.metadata.{CarbonMetadata, SegmentFileStore}
import org.apache.carbondata.core.util.path.CarbonTablePath

class TestRangeColumnDataLoad extends QueryTest with BeforeAndAfterEach with BeforeAndAfterAll {
  var filePath: String = s"$resourcesPath/globalsort"

  override def beforeAll(): Unit = {
    dropTable
  }

  override def afterAll(): Unit = {
    dropTable
  }

  def dropTable(): Unit = {
    sql("DROP TABLE IF EXISTS carbon_range_column1")
    sql("DROP TABLE IF EXISTS carbon_range_column2")
    sql("DROP TABLE IF EXISTS carbon_range_column3")
    sql("DROP TABLE IF EXISTS carbon_range_column4")
  }

  test("range_column with option GLOBAL_SORT_PARTITIONS") {
    sql(
      """
        | CREATE TABLE carbon_range_column1(id INT, name STRING, city STRING, age INT)
        | STORED BY 'org.apache.carbondata.format'
        | TBLPROPERTIES('SORT_SCOPE'='LOCAL_SORT', 'SORT_COLUMNS'='name, city')
      """.stripMargin)

    sql(s"LOAD DATA LOCAL INPATH '$filePath' INTO TABLE carbon_range_column1 " +
        "OPTIONS('GLOBAL_SORT_PARTITIONS'='1', 'range_column'='name')")

    assert(getIndexFileCount("carbon_range_column1") === 1)
    checkAnswer(sql("SELECT COUNT(*) FROM carbon_range_column1"), Seq(Row(12)))
    checkAnswer(sql("SELECT * FROM carbon_range_column1"),
      sql("SELECT * FROM carbon_range_column1 ORDER BY name"))
  }

  test("range_column with option scale_factor") {
    sql(
      """
        | CREATE TABLE carbon_range_column2(id INT, name STRING, city STRING, age INT)
        | STORED BY 'org.apache.carbondata.format'
        | TBLPROPERTIES('SORT_SCOPE'='LOCAL_SORT', 'SORT_COLUMNS'='name, city')
      """.stripMargin)

    sql(s"LOAD DATA LOCAL INPATH '$filePath' INTO TABLE carbon_range_column2 " +
        "OPTIONS('scale_factor'='10', 'range_column'='name')")

    assert(getIndexFileCount("carbon_range_column2") === 1)
    checkAnswer(sql("SELECT COUNT(*) FROM carbon_range_column2"), Seq(Row(12)))
    checkAnswer(sql("SELECT * FROM carbon_range_column2"),
      sql("SELECT * FROM carbon_range_column2 ORDER BY name"))
  }

  test("range_column only support single column ") {
    sql(
      """
        | CREATE TABLE carbon_range_column3(id INT, name STRING, city STRING, age INT)
        | STORED BY 'org.apache.carbondata.format'
        | TBLPROPERTIES('SORT_SCOPE'='LOCAL_SORT', 'SORT_COLUMNS'='name, city')
      """.stripMargin)

    intercept[InvalidLoadOptionException] {
      sql(s"LOAD DATA LOCAL INPATH '$filePath' INTO TABLE carbon_range_column3 " +
          "OPTIONS('scale_factor'='10', 'range_column'='name,id')")
    }
  }

  test("range_column with data skew") {
    sql(
      """
        | CREATE TABLE carbon_range_column4(id INT, name STRING, city STRING, age INT)
        | STORED BY 'org.apache.carbondata.format'
        | TBLPROPERTIES('SORT_SCOPE'='GLOBAL_SORT', 'SORT_COLUMNS'='name, city')
      """.stripMargin)

    val dataSkewPath = s"$resourcesPath/range_column"

    sql(s"LOAD DATA LOCAL INPATH '$dataSkewPath' INTO TABLE carbon_range_column4 " +
        "OPTIONS('GLOBAL_SORT_PARTITIONS'='5', 'range_column'='name', " +
        "'BAD_RECORDS_ACTION'='force')")

    assert(getIndexFileCount("carbon_range_column4") === 5)
    checkAnswer(sql("SELECT COUNT(*) FROM carbon_range_column4"), Seq(Row(10)))
  }

  private def getIndexFileCount(tableName: String, segmentNo: String = "0"): Int = {
    val carbonTable = CarbonMetadata.getInstance().getCarbonTable("default", tableName)
    val segmentDir = CarbonTablePath.getSegmentPath(carbonTable.getTablePath, segmentNo)
    if (FileFactory.isFileExist(segmentDir)) {
      new SegmentIndexFileStore().getIndexFilesFromSegment(segmentDir).size()
    } else {
      val segment = Segment.getSegment(segmentNo, carbonTable.getTablePath)
      new SegmentFileStore(carbonTable.getTablePath, segment.getSegmentFileName).getIndexCarbonFiles
        .size()
    }
  }
}
