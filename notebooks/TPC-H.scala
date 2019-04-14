// Databricks notebook source
System.setProperty("spline.mode", "REQUIRED")
System.setProperty("spline.persistence.factory", "za.co.absa.spline.persistence.mongo.MongoPersistenceFactory")
System.setProperty("spline.mongodb.url", dbutils.secrets.get("spline", "spline.mongodb.url"))
import za.co.absa.spline.core.SparkLineageInitializer._
spark.enableLineageTracking()

// COMMAND ----------

val dbname = "lineage_tutorial_" + java.util.UUID.randomUUID.toString.replaceAll("-","")
spark.sql(s"CREATE DATABASE $dbname")
spark.sql(s"USE $dbname")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_nation (n_nationkey int, n_name string, n_regionkey int, n_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/nation/nation.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_region (r_regionkey int, r_name string, r_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/region/region.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_orders (o_orderkey int, o_custkey int, o_orderstatus string, o_totalprice double, o_orderdate string, o_orderpriority string, o_clerk string, o_shippriority int, o_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/orders/orders.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_lineitem (l_orderkey int, l_partkey int, l_suppkey int, l_linenumber int, l_quantity double, l_extendedprice double, l_discount double, l_tax double, l_returnflag string,  l_linestatus string, l_shipdate string, l_commitdate string, l_receiptdate string, l_shipinstruct string, l_shipmode string, l_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/lineitem/lineitem.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_customer (c_custkey int, c_name string, c_address string, c_nationkey int, c_phone string, c_acctbal double, c_mktsegment string , c_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/customer/customer.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_part (p_partkey int, p_name string, p_mfgr string, p_brand string, p_type string, p_size int, p_container string, p_retailprice double, p_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/part/part.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_supplier (s_suppkey int, s_name string, s_address string, s_nationkey int, s_phone string, s_acctbal double, s_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/supplier/supplier.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TEMPORARY TABLE raw_partsupp (ps_partkey int, ps_suppkey int, ps_availqty int, ps_supplycost decimal, ps_comment string)
// MAGIC USING com.databricks.spark.csv
// MAGIC OPTIONS (path "/databricks-datasets/tpch/data-001/partsupp/partsupp.tbl", header "false", delimiter "|")

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE nation AS SELECT * FROM raw_nation

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE region AS SELECT * FROM raw_region

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE orders AS SELECT * FROM raw_orders

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE lineitem AS SELECT * FROM raw_lineitem

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE customer AS SELECT * FROM raw_customer

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE part AS SELECT * FROM raw_part

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE supplier AS SELECT * FROM raw_supplier

// COMMAND ----------

// MAGIC %sql
// MAGIC CREATE TABLE partsupp AS SELECT * FROM raw_partsupp

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Pricing Summary Report Query (Q1)
-- Functional Query Definition
-- Approved February 1998


select
	l_returnflag,
	l_linestatus,
	sum(l_quantity) as sum_qty,
	sum(l_extendedprice) as sum_base_price,
	sum(l_extendedprice * (1 - l_discount)) as sum_disc_price,
	sum(l_extendedprice * (1 - l_discount) * (1 + l_tax)) as sum_charge,
	avg(l_quantity) as avg_qty,
	avg(l_extendedprice) as avg_price,
	avg(l_discount) as avg_disc,
	count(*) as count_order
from
	lineitem
where
	l_shipdate <= date '1998-12-01' - interval '90' day
group by
	l_returnflag,
	l_linestatus
order by
	l_returnflag,
	l_linestatus


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH1.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Minimum Cost Supplier Query (Q2)
-- Functional Query Definition
-- Approved February 1998


select
	s_acctbal,
	s_name,
	n_name,
	p_partkey,
	p_mfgr,
	s_address,
	s_phone,
	s_comment
from
	part,
	supplier,
	partsupp,
	nation,
	region
where
	p_partkey = ps_partkey
	and s_suppkey = ps_suppkey
	and p_size = 15
	and p_type like '%BRASS'
	and s_nationkey = n_nationkey
	and n_regionkey = r_regionkey
	and r_name = 'EUROPE'
	and ps_supplycost = (
		select
			min(ps_supplycost)
		from
			partsupp,
			supplier,
			nation,
			region
		where
			p_partkey = ps_partkey
			and s_suppkey = ps_suppkey
			and s_nationkey = n_nationkey
			and n_regionkey = r_regionkey
			and r_name = 'EUROPE'
	)
order by
	s_acctbal desc,
	n_name,
	s_name,
	p_partkey
LIMIT  100

""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH2.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Shipping Priority Query (Q3)
-- Functional Query Definition
-- Approved February 1998


select
	l_orderkey,
	sum(l_extendedprice * (1 - l_discount)) as revenue,
	o_orderdate,
	o_shippriority
from
	customer,
	orders,
	lineitem
where
	c_mktsegment = 'BUILDING'
	and c_custkey = o_custkey
	and l_orderkey = o_orderkey
	and o_orderdate < date '1995-03-15'
	and l_shipdate > date '1995-03-15'
group by
	l_orderkey,
	o_orderdate,
	o_shippriority
order by
	revenue desc,
	o_orderdate
LIMIT  10

""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH3.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Order Priority Checking Query (Q4)
-- Functional Query Definition
-- Approved February 1998


select
	o_orderpriority,
	count(*) as order_count
from
	orders
where
	o_orderdate >= date '1993-07-01'
	and o_orderdate < date '1993-07-01' + interval '3' month
	and exists (
		select
			*
		from
			lineitem
		where
			l_orderkey = o_orderkey
			and l_commitdate < l_receiptdate
	)
group by
	o_orderpriority
order by
	o_orderpriority


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH4.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Local Supplier Volume Query (Q5)
-- Functional Query Definition
-- Approved February 1998


select
	n_name,
	sum(l_extendedprice * (1 - l_discount)) as revenue
from
	customer,
	orders,
	lineitem,
	supplier,
	nation,
	region
where
	c_custkey = o_custkey
	and l_orderkey = o_orderkey
	and l_suppkey = s_suppkey
	and c_nationkey = s_nationkey
	and s_nationkey = n_nationkey
	and n_regionkey = r_regionkey
	and r_name = 'ASIA'
	and o_orderdate >= date '1994-01-01'
	and o_orderdate < date '1994-01-01' + interval '1' year
group by
	n_name
order by
	revenue desc


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH5.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Forecasting Revenue Change Query (Q6)
-- Functional Query Definition
-- Approved February 1998


select
	sum(l_extendedprice * l_discount) as revenue
from
	lineitem
where
	l_shipdate >= date '1994-01-01'
	and l_shipdate < date '1994-01-01' + interval '1' year
	and l_discount between .06 - 0.01 and .06 + 0.01
	and l_quantity < 24


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH6.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Volume Shipping Query (Q7)
-- Functional Query Definition
-- Approved February 1998


select
	supp_nation,
	cust_nation,
	l_year,
	sum(volume) as revenue
from
	(
		select
			n1.n_name as supp_nation,
			n2.n_name as cust_nation,
			extract(year from l_shipdate) as l_year,
			l_extendedprice * (1 - l_discount) as volume
		from
			supplier,
			lineitem,
			orders,
			customer,
			nation n1,
			nation n2
		where
			s_suppkey = l_suppkey
			and o_orderkey = l_orderkey
			and c_custkey = o_custkey
			and s_nationkey = n1.n_nationkey
			and c_nationkey = n2.n_nationkey
			and (
				(n1.n_name = 'FRANCE' and n2.n_name = 'GERMANY')
				or (n1.n_name = 'GERMANY' and n2.n_name = 'FRANCE')
			)
			and l_shipdate between date '1995-01-01' and date '1996-12-31'
	) as shipping
group by
	supp_nation,
	cust_nation,
	l_year
order by
	supp_nation,
	cust_nation,
	l_year


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH7.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R National Market Share Query (Q8)
-- Functional Query Definition
-- Approved February 1998


select
	o_year,
	sum(case
		when nation = 'BRAZIL' then volume
		else 0
	end) / sum(volume) as mkt_share
from
	(
		select
			extract(year from o_orderdate) as o_year,
			l_extendedprice * (1 - l_discount) as volume,
			n2.n_name as nation
		from
			part,
			supplier,
			lineitem,
			orders,
			customer,
			nation n1,
			nation n2,
			region
		where
			p_partkey = l_partkey
			and s_suppkey = l_suppkey
			and l_orderkey = o_orderkey
			and o_custkey = c_custkey
			and c_nationkey = n1.n_nationkey
			and n1.n_regionkey = r_regionkey
			and r_name = 'AMERICA'
			and s_nationkey = n2.n_nationkey
			and o_orderdate between date '1995-01-01' and date '1996-12-31'
			and p_type = 'ECONOMY ANODIZED STEEL'
	) as all_nations
group by
	o_year
order by
	o_year


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH8.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Product Type Profit Measure Query (Q9)
-- Functional Query Definition
-- Approved February 1998


select
	nation,
	o_year,
	sum(amount) as sum_profit
from
	(
		select
			n_name as nation,
			extract(year from o_orderdate) as o_year,
			l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
		from
			part,
			supplier,
			lineitem,
			partsupp,
			orders,
			nation
		where
			s_suppkey = l_suppkey
			and ps_suppkey = l_suppkey
			and ps_partkey = l_partkey
			and p_partkey = l_partkey
			and o_orderkey = l_orderkey
			and s_nationkey = n_nationkey
			and p_name like '%green%'
	) as profit
group by
	nation,
	o_year
order by
	nation,
	o_year desc


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH9.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Returned Item Reporting Query (Q10)
-- Functional Query Definition
-- Approved February 1998


select
	c_custkey,
	c_name,
	sum(l_extendedprice * (1 - l_discount)) as revenue,
	c_acctbal,
	n_name,
	c_address,
	c_phone,
	c_comment
from
	customer,
	orders,
	lineitem,
	nation
where
	c_custkey = o_custkey
	and l_orderkey = o_orderkey
	and o_orderdate >= date '1993-10-01'
	and o_orderdate < date '1993-10-01' + interval '3' month
	and l_returnflag = 'R'
	and c_nationkey = n_nationkey
group by
	c_custkey,
	c_name,
	c_acctbal,
	c_phone,
	n_name,
	c_address,
	c_comment
order by
	revenue desc
LIMIT  20

""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH10.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Important Stock Identification Query (Q11)
-- Functional Query Definition
-- Approved February 1998


select
	ps_partkey,
	sum(ps_supplycost * ps_availqty) as value
from
	partsupp,
	supplier,
	nation
where
	ps_suppkey = s_suppkey
	and s_nationkey = n_nationkey
	and n_name = 'GERMANY'
group by
	ps_partkey having
		sum(ps_supplycost * ps_availqty) > (
			select
				sum(ps_supplycost * ps_availqty) * 0.0001
			from
				partsupp,
				supplier,
				nation
			where
				ps_suppkey = s_suppkey
				and s_nationkey = n_nationkey
				and n_name = 'GERMANY'
		)
order by
	value desc


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH11.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Shipping Modes and Order Priority Query (Q12)
-- Functional Query Definition
-- Approved February 1998


select
	l_shipmode,
	sum(case
		when o_orderpriority = '1-URGENT'
			or o_orderpriority = '2-HIGH'
			then 1
		else 0
	end) as high_line_count,
	sum(case
		when o_orderpriority <> '1-URGENT'
			and o_orderpriority <> '2-HIGH'
			then 1
		else 0
	end) as low_line_count
from
	orders,
	lineitem
where
	o_orderkey = l_orderkey
	and l_shipmode in ('MAIL', 'SHIP')
	and l_commitdate < l_receiptdate
	and l_shipdate < l_commitdate
	and l_receiptdate >= date '1994-01-01'
	and l_receiptdate < date '1994-01-01' + interval '1' year
group by
	l_shipmode
order by
	l_shipmode


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH12.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Customer Distribution Query (Q13)
-- Functional Query Definition
-- Approved February 1998


select
	c_count,
	count(*) as custdist
from
	(
		select
			c_custkey,
			count(o_orderkey)
		from
			customer left outer join orders on
				c_custkey = o_custkey
				and o_comment not like '%special%requests%'
		group by
			c_custkey
	) as c_orders (c_custkey, c_count)
group by
	c_count
order by
	custdist desc,
	c_count desc


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH13.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Promotion Effect Query (Q14)
-- Functional Query Definition
-- Approved February 1998


select
	100.00 * sum(case
		when p_type like 'PROMO%'
			then l_extendedprice * (1 - l_discount)
		else 0
	end) / sum(l_extendedprice * (1 - l_discount)) as promo_revenue
from
	lineitem,
	part
where
	l_partkey = p_partkey
	and l_shipdate >= date '1995-09-01'
	and l_shipdate < date '1995-09-01' + interval '1' month


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH14.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Parts/Supplier Relationship Query (Q16)
-- Functional Query Definition
-- Approved February 1998


select
	p_brand,
	p_type,
	p_size,
	count(distinct ps_suppkey) as supplier_cnt
from
	partsupp,
	part
where
	p_partkey = ps_partkey
	and p_brand <> 'Brand#45'
	and p_type not like 'MEDIUM POLISHED%'
	and p_size in (49, 14, 23, 45, 19, 3, 36, 9)
	and ps_suppkey not in (
		select
			s_suppkey
		from
			supplier
		where
			s_comment like '%Customer%Complaints%'
	)
group by
	p_brand,
	p_type,
	p_size
order by
	supplier_cnt desc,
	p_brand,
	p_type,
	p_size


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH16.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Small-Quantity-Order Revenue Query (Q17)
-- Functional Query Definition
-- Approved February 1998


select
	sum(l_extendedprice) / 7.0 as avg_yearly
from
	lineitem,
	part
where
	p_partkey = l_partkey
	and p_brand = 'Brand#23'
	and p_container = 'MED BOX'
	and l_quantity < (
		select
			0.2 * avg(l_quantity)
		from
			lineitem
		where
			l_partkey = p_partkey
	)


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH17.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Large Volume Customer Query (Q18)
-- Function Query Definition
-- Approved February 1998


select
	c_name,
	c_custkey,
	o_orderkey,
	o_orderdate,
	o_totalprice,
	sum(l_quantity) AS sum_l_quantity
from
	customer,
	orders,
	lineitem
where
	o_orderkey in (
		select
			l_orderkey
		from
			lineitem
		group by
			l_orderkey having
				sum(l_quantity) > 300
	)
	and c_custkey = o_custkey
	and o_orderkey = l_orderkey
group by
	c_name,
	c_custkey,
	o_orderkey,
	o_orderdate,
	o_totalprice
order by
	o_totalprice desc,
	o_orderdate
LIMIT  100

""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH18.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Discounted Revenue Query (Q19)
-- Functional Query Definition
-- Approved February 1998


select
	sum(l_extendedprice* (1 - l_discount)) as revenue
from
	lineitem,
	part
where
	(
		p_partkey = l_partkey
		and p_brand = 'Brand#12'
		and p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
		and l_quantity >= 1 and l_quantity <= 1 + 10
		and p_size between 1 and 5
		and l_shipmode in ('AIR', 'AIR REG')
		and l_shipinstruct = 'DELIVER IN PERSON'
	)
	or
	(
		p_partkey = l_partkey
		and p_brand = 'Brand#23'
		and p_container in ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
		and l_quantity >= 10 and l_quantity <= 10 + 10
		and p_size between 1 and 10
		and l_shipmode in ('AIR', 'AIR REG')
		and l_shipinstruct = 'DELIVER IN PERSON'
	)
	or
	(
		p_partkey = l_partkey
		and p_brand = 'Brand#34'
		and p_container in ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
		and l_quantity >= 20 and l_quantity <= 20 + 10
		and p_size between 1 and 15
		and l_shipmode in ('AIR', 'AIR REG')
		and l_shipinstruct = 'DELIVER IN PERSON'
	)


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH19.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Potential Part Promotion Query (Q20)
-- Function Query Definition
-- Approved February 1998


select
	s_name,
	s_address
from
	supplier,
	nation
where
	s_suppkey in (
		select
			ps_suppkey
		from
			partsupp
		where
			ps_partkey in (
				select
					p_partkey
				from
					part
				where
					p_name like 'forest%'
			)
			and ps_availqty > (
				select
					0.5 * sum(l_quantity)
				from
					lineitem
				where
					l_partkey = ps_partkey
					and l_suppkey = ps_suppkey
					and l_shipdate >= date '1994-01-01'
					and l_shipdate < date '1994-01-01' + interval '1' year
			)
	)
	and s_nationkey = n_nationkey
	and n_name = 'CANADA'
order by
	s_name


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH20.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Suppliers Who Kept Orders Waiting Query (Q21)
-- Functional Query Definition
-- Approved February 1998


select
	s_name,
	count(*) as numwait
from
	supplier,
	lineitem l1,
	orders,
	nation
where
	s_suppkey = l1.l_suppkey
	and o_orderkey = l1.l_orderkey
	and o_orderstatus = 'F'
	and l1.l_receiptdate > l1.l_commitdate
	and exists (
		select
			*
		from
			lineitem l2
		where
			l2.l_orderkey = l1.l_orderkey
			and l2.l_suppkey <> l1.l_suppkey
	)
	and not exists (
		select
			*
		from
			lineitem l3
		where
			l3.l_orderkey = l1.l_orderkey
			and l3.l_suppkey <> l1.l_suppkey
			and l3.l_receiptdate > l3.l_commitdate
	)
	and s_nationkey = n_nationkey
	and n_name = 'SAUDI ARABIA'
group by
	s_name
order by
	numwait desc,
	s_name
LIMIT  100

""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH21.parquet")

// COMMAND ----------

spark.sql("""
-- 
-- TPC-H/TPC-R Global Sales Opportunity Query (Q22)
-- Functional Query Definition
-- Approved February 1998


select
	cntrycode,
	count(*) as numcust,
	sum(c_acctbal) as totacctbal
from
	(
		select
			substring(c_phone, 0, 2) as cntrycode,
			c_acctbal
		from
			customer
		where
			substring(c_phone, 0, 2) in
				('13', '31', '23', '29', '30', '18', '17')
			and c_acctbal > (
				select
					avg(c_acctbal)
				from
					customer
				where
					c_acctbal > 0.00
					and substring(c_phone, 0, 2) in
						('13', '31', '23', '29', '30', '18', '17')
			)
			and not exists (
				select
					*
				from
					orders
				where
					o_custkey = c_custkey
			)
	) as custsale
group by
	cntrycode
order by
	cntrycode


""").write.mode("overwrite").parquet("/tmp/tpch/TPCDH22.parquet")
