-- ============================================================
-- 数据库: Kettle 业务库
-- 数据库类型: MySQL 8.0.19
-- 生成工具: screw-web DdlService
-- 生成时间: Fri Sep 04 19:34:55 CST 2026
-- ============================================================

-- ------------------------------------------------------------
-- 表: r_cluster
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_cluster;
CREATE TABLE r_cluster (
    ID_CLUSTER BIGINT NOT NULL,
    NAME VARCHAR(255),
    BASE_PORT VARCHAR(255),
    SOCKETS_BUFFER_SIZE VARCHAR(255),
    SOCKETS_FLUSH_INTERVAL VARCHAR(255),
    SOCKETS_COMPRESSED BIT(1),
    DYNAMIC_CLUSTER BIT(1),
    PRIMARY KEY (ID_CLUSTER)
);

-- ------------------------------------------------------------
-- 表: r_cluster_slave
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_cluster_slave;
CREATE TABLE r_cluster_slave (
    ID_CLUSTER_SLAVE BIGINT NOT NULL,
    ID_CLUSTER INT,
    ID_SLAVE INT,
    PRIMARY KEY (ID_CLUSTER_SLAVE)
);

-- ------------------------------------------------------------
-- 表: r_condition
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_condition;
CREATE TABLE r_condition (
    ID_CONDITION BIGINT NOT NULL,
    ID_CONDITION_PARENT INT,
    NEGATED BIT(1),
    OPERATOR VARCHAR(255),
    LEFT_NAME VARCHAR(255),
    CONDITION_FUNCTION VARCHAR(255),
    RIGHT_NAME VARCHAR(255),
    ID_VALUE_RIGHT INT,
    PRIMARY KEY (ID_CONDITION)
);

-- ------------------------------------------------------------
-- 表: r_database
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_database;
CREATE TABLE r_database (
    ID_DATABASE BIGINT NOT NULL,
    NAME VARCHAR(255),
    ID_DATABASE_TYPE INT,
    ID_DATABASE_CONTYPE INT,
    HOST_NAME VARCHAR(255),
    DATABASE_NAME MEDIUMTEXT,
    PORT INT,
    USERNAME VARCHAR(255),
    PASSWORD VARCHAR(255),
    SERVERNAME VARCHAR(255),
    DATA_TBS VARCHAR(255),
    INDEX_TBS VARCHAR(255),
    PRIMARY KEY (ID_DATABASE)
);

-- ------------------------------------------------------------
-- 表: r_database_attribute
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_database_attribute;
CREATE TABLE r_database_attribute (
    ID_DATABASE_ATTRIBUTE BIGINT NOT NULL,
    ID_DATABASE INT,
    CODE VARCHAR(255),
    VALUE_STR MEDIUMTEXT,
    PRIMARY KEY (ID_DATABASE_ATTRIBUTE)
);
CREATE UNIQUE INDEX IDX_RDAT ON r_database_attribute (ID_DATABASE, CODE);

-- ------------------------------------------------------------
-- 表: r_database_contype
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_database_contype;
CREATE TABLE r_database_contype (
    ID_DATABASE_CONTYPE BIGINT NOT NULL,
    CODE VARCHAR(255),
    DESCRIPTION VARCHAR(255),
    PRIMARY KEY (ID_DATABASE_CONTYPE)
);

-- ------------------------------------------------------------
-- 表: r_database_type
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_database_type;
CREATE TABLE r_database_type (
    ID_DATABASE_TYPE BIGINT NOT NULL,
    CODE VARCHAR(255),
    DESCRIPTION VARCHAR(255),
    PRIMARY KEY (ID_DATABASE_TYPE)
);

-- ------------------------------------------------------------
-- 表: r_dependency
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_dependency;
CREATE TABLE r_dependency (
    ID_DEPENDENCY BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    ID_DATABASE INT,
    TABLE_NAME VARCHAR(255),
    FIELD_NAME VARCHAR(255),
    PRIMARY KEY (ID_DEPENDENCY)
);

-- ------------------------------------------------------------
-- 表: r_directory
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_directory;
CREATE TABLE r_directory (
    ID_DIRECTORY BIGINT NOT NULL,
    ID_DIRECTORY_PARENT INT,
    DIRECTORY_NAME VARCHAR(255),
    PRIMARY KEY (ID_DIRECTORY)
);
CREATE UNIQUE INDEX IDX_RDIR ON r_directory (ID_DIRECTORY_PARENT, DIRECTORY_NAME);

-- ------------------------------------------------------------
-- 表: r_element
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_element;
CREATE TABLE r_element (
    ID_ELEMENT BIGINT NOT NULL,
    ID_ELEMENT_TYPE INT,
    NAME TEXT,
    PRIMARY KEY (ID_ELEMENT)
);

-- ------------------------------------------------------------
-- 表: r_element_attribute
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_element_attribute;
CREATE TABLE r_element_attribute (
    ID_ELEMENT_ATTRIBUTE BIGINT NOT NULL,
    ID_ELEMENT INT,
    ID_ELEMENT_ATTRIBUTE_PARENT INT,
    ATTR_KEY VARCHAR(255),
    ATTR_VALUE MEDIUMTEXT,
    PRIMARY KEY (ID_ELEMENT_ATTRIBUTE)
);

-- ------------------------------------------------------------
-- 表: r_element_type
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_element_type;
CREATE TABLE r_element_type (
    ID_ELEMENT_TYPE BIGINT NOT NULL,
    ID_NAMESPACE INT,
    NAME TEXT,
    DESCRIPTION MEDIUMTEXT,
    PRIMARY KEY (ID_ELEMENT_TYPE)
);

-- ------------------------------------------------------------
-- 表: r_job
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_job;
CREATE TABLE r_job (
    ID_JOB BIGINT NOT NULL,
    ID_DIRECTORY INT,
    NAME VARCHAR(255),
    DESCRIPTION MEDIUMTEXT,
    EXTENDED_DESCRIPTION MEDIUMTEXT,
    JOB_VERSION VARCHAR(255),
    JOB_STATUS INT,
    ID_DATABASE_LOG INT,
    TABLE_NAME_LOG VARCHAR(255),
    CREATED_USER VARCHAR(255),
    CREATED_DATE DATETIME,
    MODIFIED_USER VARCHAR(255),
    MODIFIED_DATE DATETIME,
    USE_BATCH_ID BIT(1),
    PASS_BATCH_ID BIT(1),
    USE_LOGFIELD BIT(1),
    SHARED_FILE VARCHAR(255),
    PRIMARY KEY (ID_JOB)
);

-- ------------------------------------------------------------
-- 表: r_job_attribute
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_job_attribute;
CREATE TABLE r_job_attribute (
    ID_JOB_ATTRIBUTE BIGINT NOT NULL,
    ID_JOB INT,
    NR INT,
    CODE VARCHAR(255),
    VALUE_NUM BIGINT,
    VALUE_STR MEDIUMTEXT,
    PRIMARY KEY (ID_JOB_ATTRIBUTE)
);
CREATE UNIQUE INDEX IDX_JATT ON r_job_attribute (ID_JOB, CODE, NR);

-- ------------------------------------------------------------
-- 表: r_job_hop
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_job_hop;
CREATE TABLE r_job_hop (
    ID_JOB_HOP BIGINT NOT NULL,
    ID_JOB INT,
    ID_JOBENTRY_COPY_FROM INT,
    ID_JOBENTRY_COPY_TO INT,
    ENABLED BIT(1),
    EVALUATION BIT(1),
    UNCONDITIONAL BIT(1),
    PRIMARY KEY (ID_JOB_HOP)
);

-- ------------------------------------------------------------
-- 表: r_job_lock
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_job_lock;
CREATE TABLE r_job_lock (
    ID_JOB_LOCK BIGINT NOT NULL,
    ID_JOB INT,
    ID_USER INT,
    LOCK_MESSAGE MEDIUMTEXT,
    LOCK_DATE DATETIME,
    PRIMARY KEY (ID_JOB_LOCK)
);

-- ------------------------------------------------------------
-- 表: r_job_note
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_job_note;
CREATE TABLE r_job_note (
    ID_JOB INT,
    ID_NOTE INT
);

-- ------------------------------------------------------------
-- 表: r_jobentry
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_jobentry;
CREATE TABLE r_jobentry (
    ID_JOBENTRY BIGINT NOT NULL,
    ID_JOB INT,
    ID_JOBENTRY_TYPE INT,
    NAME VARCHAR(255),
    DESCRIPTION MEDIUMTEXT,
    PRIMARY KEY (ID_JOBENTRY)
);

-- ------------------------------------------------------------
-- 表: r_jobentry_attribute
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_jobentry_attribute;
CREATE TABLE r_jobentry_attribute (
    ID_JOBENTRY_ATTRIBUTE BIGINT NOT NULL,
    ID_JOB INT,
    ID_JOBENTRY INT,
    NR INT,
    CODE VARCHAR(255),
    VALUE_NUM DOUBLE,
    VALUE_STR MEDIUMTEXT,
    PRIMARY KEY (ID_JOBENTRY_ATTRIBUTE)
);
CREATE UNIQUE INDEX IDX_RJEA ON r_jobentry_attribute (ID_JOBENTRY_ATTRIBUTE, CODE, NR);

-- ------------------------------------------------------------
-- 表: r_jobentry_copy
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_jobentry_copy;
CREATE TABLE r_jobentry_copy (
    ID_JOBENTRY_COPY BIGINT NOT NULL,
    ID_JOBENTRY INT,
    ID_JOB INT,
    ID_JOBENTRY_TYPE INT,
    NR INT,
    GUI_LOCATION_X INT,
    GUI_LOCATION_Y INT,
    GUI_DRAW BIT(1),
    PARALLEL BIT(1),
    PRIMARY KEY (ID_JOBENTRY_COPY)
);

-- ------------------------------------------------------------
-- 表: r_jobentry_database
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_jobentry_database;
CREATE TABLE r_jobentry_database (
    ID_JOB INT,
    ID_JOBENTRY INT,
    ID_DATABASE INT
);
CREATE INDEX IDX_RJD1 ON r_jobentry_database (ID_JOB);
CREATE INDEX IDX_RJD2 ON r_jobentry_database (ID_DATABASE);

-- ------------------------------------------------------------
-- 表: r_jobentry_type
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_jobentry_type;
CREATE TABLE r_jobentry_type (
    ID_JOBENTRY_TYPE BIGINT NOT NULL,
    CODE VARCHAR(255),
    DESCRIPTION VARCHAR(255),
    PRIMARY KEY (ID_JOBENTRY_TYPE)
);

-- ------------------------------------------------------------
-- 表: r_log
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_log;
CREATE TABLE r_log (
    ID_LOG BIGINT NOT NULL,
    NAME VARCHAR(255),
    ID_LOGLEVEL INT,
    LOGTYPE VARCHAR(255),
    FILENAME VARCHAR(255),
    FILEEXTENTION VARCHAR(255),
    ADD_DATE BIT(1),
    ADD_TIME BIT(1),
    ID_DATABASE_LOG INT,
    TABLE_NAME_LOG VARCHAR(255),
    PRIMARY KEY (ID_LOG)
);

-- ------------------------------------------------------------
-- 表: r_loglevel
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_loglevel;
CREATE TABLE r_loglevel (
    ID_LOGLEVEL BIGINT NOT NULL,
    CODE VARCHAR(255),
    DESCRIPTION VARCHAR(255),
    PRIMARY KEY (ID_LOGLEVEL)
);

-- ------------------------------------------------------------
-- 表: r_namespace
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_namespace;
CREATE TABLE r_namespace (
    ID_NAMESPACE BIGINT NOT NULL,
    NAME TEXT,
    PRIMARY KEY (ID_NAMESPACE)
);

-- ------------------------------------------------------------
-- 表: r_note
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_note;
CREATE TABLE r_note (
    ID_NOTE BIGINT NOT NULL,
    VALUE_STR MEDIUMTEXT,
    GUI_LOCATION_X INT,
    GUI_LOCATION_Y INT,
    GUI_LOCATION_WIDTH INT,
    GUI_LOCATION_HEIGHT INT,
    FONT_NAME MEDIUMTEXT,
    FONT_SIZE INT,
    FONT_BOLD BIT(1),
    FONT_ITALIC BIT(1),
    FONT_COLOR_RED INT,
    FONT_COLOR_GREEN INT,
    FONT_COLOR_BLUE INT,
    FONT_BACK_GROUND_COLOR_RED INT,
    FONT_BACK_GROUND_COLOR_GREEN INT,
    FONT_BACK_GROUND_COLOR_BLUE INT,
    FONT_BORDER_COLOR_RED INT,
    FONT_BORDER_COLOR_GREEN INT,
    FONT_BORDER_COLOR_BLUE INT,
    DRAW_SHADOW BIT(1),
    PRIMARY KEY (ID_NOTE)
);

-- ------------------------------------------------------------
-- 表: r_partition
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_partition;
CREATE TABLE r_partition (
    ID_PARTITION BIGINT NOT NULL,
    ID_PARTITION_SCHEMA INT,
    PARTITION_ID VARCHAR(255),
    PRIMARY KEY (ID_PARTITION)
);

-- ------------------------------------------------------------
-- 表: r_partition_schema
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_partition_schema;
CREATE TABLE r_partition_schema (
    ID_PARTITION_SCHEMA BIGINT NOT NULL,
    NAME VARCHAR(255),
    DYNAMIC_DEFINITION BIT(1),
    PARTITIONS_PER_SLAVE VARCHAR(255),
    PRIMARY KEY (ID_PARTITION_SCHEMA)
);

-- ------------------------------------------------------------
-- 表: r_repository_log
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_repository_log;
CREATE TABLE r_repository_log (
    ID_REPOSITORY_LOG BIGINT NOT NULL,
    REP_VERSION VARCHAR(255),
    LOG_DATE DATETIME,
    LOG_USER VARCHAR(255),
    OPERATION_DESC MEDIUMTEXT,
    PRIMARY KEY (ID_REPOSITORY_LOG)
);

-- ------------------------------------------------------------
-- 表: r_slave
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_slave;
CREATE TABLE r_slave (
    ID_SLAVE BIGINT NOT NULL,
    NAME VARCHAR(255),
    HOST_NAME VARCHAR(255),
    PORT VARCHAR(255),
    WEB_APP_NAME VARCHAR(255),
    USERNAME VARCHAR(255),
    PASSWORD VARCHAR(255),
    PROXY_HOST_NAME VARCHAR(255),
    PROXY_PORT VARCHAR(255),
    NON_PROXY_HOSTS VARCHAR(255),
    MASTER BIT(1),
    PRIMARY KEY (ID_SLAVE)
);

-- ------------------------------------------------------------
-- 表: r_step
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_step;
CREATE TABLE r_step (
    ID_STEP BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    NAME VARCHAR(255),
    DESCRIPTION MEDIUMTEXT,
    ID_STEP_TYPE INT,
    DISTRIBUTE BIT(1),
    COPIES INT,
    GUI_LOCATION_X INT,
    GUI_LOCATION_Y INT,
    GUI_DRAW BIT(1),
    COPIES_STRING VARCHAR(255),
    PRIMARY KEY (ID_STEP)
);

-- ------------------------------------------------------------
-- 表: r_step_attribute
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_step_attribute;
CREATE TABLE r_step_attribute (
    ID_STEP_ATTRIBUTE BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    ID_STEP INT,
    NR INT,
    CODE VARCHAR(255),
    VALUE_NUM BIGINT,
    VALUE_STR MEDIUMTEXT,
    PRIMARY KEY (ID_STEP_ATTRIBUTE)
);
CREATE UNIQUE INDEX IDX_RSAT ON r_step_attribute (ID_STEP, CODE, NR);

-- ------------------------------------------------------------
-- 表: r_step_database
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_step_database;
CREATE TABLE r_step_database (
    ID_TRANSFORMATION INT,
    ID_STEP INT,
    ID_DATABASE INT
);
CREATE INDEX IDX_RSD1 ON r_step_database (ID_TRANSFORMATION);
CREATE INDEX IDX_RSD2 ON r_step_database (ID_DATABASE);

-- ------------------------------------------------------------
-- 表: r_step_type
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_step_type;
CREATE TABLE r_step_type (
    ID_STEP_TYPE BIGINT NOT NULL,
    CODE VARCHAR(255),
    DESCRIPTION VARCHAR(255),
    HELPTEXT VARCHAR(255),
    PRIMARY KEY (ID_STEP_TYPE)
);

-- ------------------------------------------------------------
-- 表: r_trans_attribute
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_attribute;
CREATE TABLE r_trans_attribute (
    ID_TRANS_ATTRIBUTE BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    NR INT,
    CODE VARCHAR(255),
    VALUE_NUM BIGINT,
    VALUE_STR MEDIUMTEXT,
    PRIMARY KEY (ID_TRANS_ATTRIBUTE)
);
CREATE UNIQUE INDEX IDX_TATT ON r_trans_attribute (ID_TRANSFORMATION, CODE, NR);

-- ------------------------------------------------------------
-- 表: r_trans_cluster
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_cluster;
CREATE TABLE r_trans_cluster (
    ID_TRANS_CLUSTER BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    ID_CLUSTER INT,
    PRIMARY KEY (ID_TRANS_CLUSTER)
);

-- ------------------------------------------------------------
-- 表: r_trans_hop
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_hop;
CREATE TABLE r_trans_hop (
    ID_TRANS_HOP BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    ID_STEP_FROM INT,
    ID_STEP_TO INT,
    ENABLED BIT(1),
    PRIMARY KEY (ID_TRANS_HOP)
);

-- ------------------------------------------------------------
-- 表: r_trans_lock
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_lock;
CREATE TABLE r_trans_lock (
    ID_TRANS_LOCK BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    ID_USER INT,
    LOCK_MESSAGE MEDIUMTEXT,
    LOCK_DATE DATETIME,
    PRIMARY KEY (ID_TRANS_LOCK)
);

-- ------------------------------------------------------------
-- 表: r_trans_note
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_note;
CREATE TABLE r_trans_note (
    ID_TRANSFORMATION INT,
    ID_NOTE INT
);

-- ------------------------------------------------------------
-- 表: r_trans_partition_schema
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_partition_schema;
CREATE TABLE r_trans_partition_schema (
    ID_TRANS_PARTITION_SCHEMA BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    ID_PARTITION_SCHEMA INT,
    PRIMARY KEY (ID_TRANS_PARTITION_SCHEMA)
);

-- ------------------------------------------------------------
-- 表: r_trans_slave
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_slave;
CREATE TABLE r_trans_slave (
    ID_TRANS_SLAVE BIGINT NOT NULL,
    ID_TRANSFORMATION INT,
    ID_SLAVE INT,
    PRIMARY KEY (ID_TRANS_SLAVE)
);

-- ------------------------------------------------------------
-- 表: r_trans_step_condition
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_trans_step_condition;
CREATE TABLE r_trans_step_condition (
    ID_TRANSFORMATION INT,
    ID_STEP INT,
    ID_CONDITION INT
);

-- ------------------------------------------------------------
-- 表: r_transformation
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_transformation;
CREATE TABLE r_transformation (
    ID_TRANSFORMATION BIGINT NOT NULL,
    ID_DIRECTORY INT,
    NAME VARCHAR(255),
    DESCRIPTION MEDIUMTEXT,
    EXTENDED_DESCRIPTION MEDIUMTEXT,
    TRANS_VERSION VARCHAR(255),
    TRANS_STATUS INT,
    ID_STEP_READ INT,
    ID_STEP_WRITE INT,
    ID_STEP_INPUT INT,
    ID_STEP_OUTPUT INT,
    ID_STEP_UPDATE INT,
    ID_DATABASE_LOG INT,
    TABLE_NAME_LOG VARCHAR(255),
    USE_BATCHID BIT(1),
    USE_LOGFIELD BIT(1),
    ID_DATABASE_MAXDATE INT,
    TABLE_NAME_MAXDATE VARCHAR(255),
    FIELD_NAME_MAXDATE VARCHAR(255),
    OFFSET_MAXDATE DOUBLE,
    DIFF_MAXDATE DOUBLE,
    CREATED_USER VARCHAR(255),
    CREATED_DATE DATETIME,
    MODIFIED_USER VARCHAR(255),
    MODIFIED_DATE DATETIME,
    SIZE_ROWSET INT,
    PRIMARY KEY (ID_TRANSFORMATION)
);

-- ------------------------------------------------------------
-- 表: r_user
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_user;
CREATE TABLE r_user (
    ID_USER BIGINT NOT NULL,
    LOGIN VARCHAR(255),
    PASSWORD VARCHAR(255),
    NAME VARCHAR(255),
    DESCRIPTION VARCHAR(255),
    ENABLED BIT(1),
    PRIMARY KEY (ID_USER)
);

-- ------------------------------------------------------------
-- 表: r_value
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_value;
CREATE TABLE r_value (
    ID_VALUE BIGINT NOT NULL,
    NAME VARCHAR(255),
    VALUE_TYPE VARCHAR(255),
    VALUE_STR VARCHAR(255),
    IS_NULL BIT(1),
    PRIMARY KEY (ID_VALUE)
);

-- ------------------------------------------------------------
-- 表: r_version
-- ------------------------------------------------------------
DROP TABLE IF EXISTS r_version;
CREATE TABLE r_version (
    ID_VERSION BIGINT NOT NULL,
    MAJOR_VERSION INT,
    MINOR_VERSION INT,
    UPGRADE_DATE DATETIME,
    IS_UPGRADE BIT(1),
    PRIMARY KEY (ID_VERSION)
);

-- ------------------------------------------------------------
-- 视图: v_r_cluster
-- 备注: VIEW
-- ------------------------------------------------------------
DROP VIEW IF EXISTS v_r_cluster;
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v_r_cluster` AS select `r_cluster`.`ID_CLUSTER` AS `ID_CLUSTER`,`r_cluster`.`NAME` AS `NAME`,`r_cluster`.`BASE_PORT` AS `BASE_PORT`,`r_cluster`.`SOCKETS_BUFFER_SIZE` AS `SOCKETS_BUFFER_SIZE`,`r_cluster`.`SOCKETS_FLUSH_INTERVAL` AS `SOCKETS_FLUSH_INTERVAL`,`r_cluster`.`SOCKETS_COMPRESSED` AS `SOCKETS_COMPRESSED`,`r_cluster`.`DYNAMIC_CLUSTER` AS `DYNAMIC_CLUSTER` from `r_cluster`;

-- ============================================================
-- 函数 / 存储过程
-- ============================================================
