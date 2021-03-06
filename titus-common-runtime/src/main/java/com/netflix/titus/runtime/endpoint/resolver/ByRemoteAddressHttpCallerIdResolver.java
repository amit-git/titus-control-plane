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

package com.netflix.titus.runtime.endpoint.resolver;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

/**
 * Extract caller id from HTTP request.
 */
public class ByRemoteAddressHttpCallerIdResolver implements HttpCallerIdResolver {

    private static final String TITUS_HEADER_CALLER_HOST_ADDRESS = "X-Titus-CallerHostAddress";

    @Override
    public Optional<String> resolve(HttpServletRequest httpServletRequest) {
        String originalCallerId = httpServletRequest.getHeader(TITUS_HEADER_CALLER_HOST_ADDRESS);
        if (originalCallerId != null) {
            return Optional.of(originalCallerId);
        }
        return Optional.ofNullable(httpServletRequest.getRemoteHost());
    }
}
