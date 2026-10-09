package com.tapapplink.sdk

internal class InMemoryKeyValueStore : KeyValueStore {
  private val strings = mutableMapOf<String, String>()
  private val booleans = mutableMapOf<String, Boolean>()

  override fun getString(key: String, default: String?): String? = strings[key] ?: default

  override fun putString(key: String, value: String) {
    strings[key] = value
  }

  override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default

  override fun putBoolean(key: String, value: Boolean) {
    booleans[key] = value
  }

  override fun remove(key: String) {
    strings.remove(key)
    booleans.remove(key)
  }

  override fun clear() {
    strings.clear()
    booleans.clear()
  }
}
