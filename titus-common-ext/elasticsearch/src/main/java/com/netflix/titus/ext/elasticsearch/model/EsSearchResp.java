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
 * Elastic search data model as defined by search API documentation
 * https://www.elastic.co/guide/en/elasticsearch/reference/5.6/search.html
 */
public class EsSearchResp<T> {
    EsRespHits<T> hits;

    public EsRespHits<T> getHits() {
        return hits;
    }

    public void setHits(EsRespHits<T> hits) {
        this.hits = hits;
    }

    @Override
    public String toString() {
        return "EsSearchResp{" +
                "hits=" + hits +
                '}';
    }
}
