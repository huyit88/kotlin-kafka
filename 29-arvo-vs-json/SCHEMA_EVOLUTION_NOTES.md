# Schema Evolution Notes

## Why Adding `merchantId` is Backward Compatible

### Backward Compatibility Explained

Adding an **optional field with a default value** is backward compatible because:

1. **Old consumers (v1) can read new records (v2)**:
   - Old consumers expect: `transactionId`, `amount`, `currency`
   - New records have: `transactionId`, `amount`, `currency`, `merchantId`
   - Old consumers simply **ignore** the unknown `merchantId` field ✅
   - They continue processing the fields they know about

2. **New consumers (v2) can read old records (v1)**:
   - New consumers expect: `transactionId`, `amount`, `currency`, `merchantId`
   - Old records have: `transactionId`, `amount`, `currency` (no `merchantId`)
   - New consumers use the **default value** (`null`) for missing `merchantId` ✅
   - They can process both old and new records seamlessly

3. **Schema Registry Compatibility**:
   - Schema Registry validates compatibility when registering new schema versions
   - Adding optional fields with defaults passes **BACKWARD compatibility** check
   - This ensures existing consumers continue working

### Example Flow

```
V1 Record (old): {transactionId: "tx-1", amount: 100.0, currency: "USD"}
  ↓
V2 Consumer reads: {transactionId: "tx-1", amount: 100.0, currency: "USD", merchantId: null}
  ✅ Works! Uses default value for missing field

V2 Record (new): {transactionId: "tx-2", amount: 50.0, currency: "EUR", merchantId: "merchant-123"}
  ↓
V1 Consumer reads: {transactionId: "tx-2", amount: 50.0, currency: "EUR"}
  ✅ Works! Ignores unknown merchantId field
```

---

## Changes That Would Break Compatibility

### Breaking Change #1: Renaming a Field

**Example**: Rename `currency` → `ccy`

```json
// V1 Schema
{
  "name": "currency",
  "type": "string"
}

// V2 Schema (BREAKING!)
{
  "name": "ccy",
  "type": "string"
}
```

**Why it breaks:**
- ❌ Old consumers look for `currency` field → **NOT FOUND** → Error
- ❌ New consumers look for `ccy` field → Old records don't have it → Error
- ❌ **Both directions fail** - no backward or forward compatibility

**Solution**: Use **aliases** instead:
```json
{
  "name": "ccy",
  "type": "string",
  "aliases": ["currency"]  // ✅ Old name still works
}
```

---

### Breaking Change #2: Changing Field Type

**Example**: Change `amount` from `double` → `int`

```json
// V1 Schema
{
  "name": "amount",
  "type": "double"
}

// V2 Schema (BREAKING!)
{
  "name": "amount",
  "type": "int"
}
```

**Why it breaks:**
- ❌ Old consumers expect `double` → New records have `int` → **Type mismatch** → Error
- ❌ New consumers expect `int` → Old records have `double` → **Type mismatch** → Error
- ❌ **Type incompatibility** prevents deserialization

**Solution**: Use **union types** for gradual migration:
```json
{
  "name": "amount",
  "type": ["double", "int"],  // ✅ Accepts both types
  "default": 0.0
}
```

---

### Additional Breaking Changes

#### #3: Removing a Required Field

**Example**: Remove `currency` field

```json
// V1 Schema
{
  "name": "currency",
  "type": "string"  // Required field
}

// V2 Schema (BREAKING!)
// currency field removed
```

**Why it breaks:**
- ❌ Old consumers expect `currency` → New records don't have it → **Missing required field** → Error

**Solution**: Mark field as **deprecated** first, then make it optional with default, then remove in later version

#### #4: Changing Field Order (Usually OK, but can cause issues)

**Note**: Avro generally handles field order changes, but it's safer to maintain order when possible.

---

## Summary

| Change Type | Backward Compatible? | Forward Compatible? |
|------------|---------------------|---------------------|
| ✅ Add optional field with default | Yes | Yes |
| ✅ Add required field with default | Yes | No |
| ❌ Remove field | No | Yes |
| ❌ Rename field | No | No |
| ❌ Change field type | No | No |
| ❌ Make optional field required | No | Yes |
| ✅ Make required field optional | Yes | Yes |

**Key Principle**: 
- **Backward compatible** = Old consumers can read new records
- **Forward compatible** = New consumers can read old records
- **Fully compatible** = Both directions work (like adding optional field with default)
