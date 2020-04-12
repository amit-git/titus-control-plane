/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.titus.ext.elasticsearch.model;

/**
 * Elastic search data model as defined by REST API documentation
 * https://www.elastic.co/guide/en/elasticsearch/reference/master/rest-apis.html
 */
public class EsRespSrc<T> {
    T _source;

    public T get_source() {
        return _source;
    }

    public void set_source(T _source) {
        this._source = _source;
    }

    @Override
    public String toString() {
        return "EsRespSrc{" +
                "_source=" + _source +
                '}';
    }
}
