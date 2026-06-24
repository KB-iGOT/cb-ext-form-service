package com.karmayogi.form.config.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;


public interface CassandraConnectionManager {
    CqlSession getSession(String keyspaceName);
}
