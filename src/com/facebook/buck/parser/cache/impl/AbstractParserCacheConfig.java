/*
 * Copyright 2018-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.parser.cache.impl;

import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.config.ConfigView;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.parser.cache.ParserCacheException;
import java.nio.file.Path;
import java.util.Optional;
import org.immutables.value.Value;

/** Class that implements the {@link com.facebook.buck.parser.cache.ParserCache} configuration. */
@Value.Immutable(builder = false, copy = false)
@BuckStyleImmutable
public abstract class AbstractParserCacheConfig implements ConfigView<BuckConfig> {
  private static final Logger LOG = Logger.get(AbstractParserCacheConfig.class);

  public static final String PARSER_CACHE_SECTION_NAME = "parser";
  public static final String PARSER_CACHE_LOCAL_LOCATION_NAME = "dir";
  public static final String PARSER_CACHE_LOCAL_MODE_NAME = "dir_mode";
  public static final String DEFAULT_PARSER_CACHE_MODE_VALUE = "NONE";

  @Override
  @Value.Parameter
  public abstract BuckConfig getDelegate();

  private ParserCacheAccessMode getCacheMode() throws ParserCacheException {
    String cacheMode =
        getDelegate()
            .getValue(PARSER_CACHE_SECTION_NAME, PARSER_CACHE_LOCAL_MODE_NAME)
            .orElse(DEFAULT_PARSER_CACHE_MODE_VALUE);
    ParserCacheAccessMode result;
    try {
      result = ParserCacheAccessMode.valueOf(cacheMode.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ParserCacheException(
          "Unusable cache.%s: '%s'", PARSER_CACHE_LOCAL_MODE_NAME, cacheMode);
    }
    return result;
  }

  /** Obtains a {@link AbstractParserDirCacheEntry} from the {@link BuckConfig}. */
  @Value.Lazy
  protected AbstractParserDirCacheEntry obtainDirEntry() {
    String dirLocation =
        getDelegate()
            .getValue(PARSER_CACHE_SECTION_NAME, PARSER_CACHE_LOCAL_LOCATION_NAME)
            .orElse(null);

    if (dirLocation == null) {
      return ParserDirCacheEntry.of(
          Optional.empty(), ParserCacheAccessMode.NONE); // Disable local cache.
    }

    ParserCacheAccessMode parserCacheAccessMode = ParserCacheAccessMode.NONE;
    try {
      parserCacheAccessMode = getCacheMode();
    } catch (ParserCacheException t) {
      LOG.error(t, "Could not get ParserCacheAccessMode for AbstractCacheConfig.");
    }

    if (parserCacheAccessMode == ParserCacheAccessMode.NONE) {
      return ParserDirCacheEntry.of(
          Optional.empty(), ParserCacheAccessMode.NONE); // Disable local cache.
    }

    Path pathToCacheDir;
    ProjectFilesystem filesystem = getDelegate().getFilesystem();
    if (dirLocation.isEmpty()) {
      pathToCacheDir = filesystem.getBuckPaths().getBuckOut().resolve(dirLocation);
    } else {
      pathToCacheDir = filesystem.getPath(dirLocation);
      if (!pathToCacheDir.isAbsolute()) {
        pathToCacheDir = filesystem.getBuckPaths().getBuckOut().resolve(pathToCacheDir);
      }
    }
    return ParserDirCacheEntry.of(Optional.of(pathToCacheDir), parserCacheAccessMode);
  }

  /** @returns the location for the local cache. */
  @Value.Lazy
  public Optional<Path> getDirCacheLocation() {
    AbstractParserDirCacheEntry parserDirCacheEntry = obtainDirEntry();
    if (parserDirCacheEntry != null) {
      return parserDirCacheEntry.getDirCacheLocation();
    }

    return Optional.empty();
  }

  /**
   * @returns {@code true} if the {@link LocalCacheStorage} is enabled, and {@code false} if not.
   */
  @Value.Lazy
  public boolean isDirParserCacheEnabled() {
    AbstractParserDirCacheEntry parserDirCacheEntry = obtainDirEntry();

    if (!parserDirCacheEntry.getDirCacheLocation().isPresent()
        || parserDirCacheEntry.getDirCacheMode() == ParserCacheAccessMode.NONE) {
      return false;
    }

    return true;
  }

  /** @returns {@link ParserCacheAccessMode} associated with remote cache storage */
  public ParserCacheAccessMode getDirCacheAccessMode() {
    AbstractParserDirCacheEntry parserDirCacheEntry = obtainDirEntry();
    if (parserDirCacheEntry != null) {
      return parserDirCacheEntry.getDirCacheMode();
    }

    return ParserCacheAccessMode.NONE;
  }
}
