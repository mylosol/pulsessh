# Database migrations

PulseSSH stores hosts, private keys, passphrases, snippets and logs in a
single Room database encrypted with SQLCipher. That data cannot be
regenerated. A user who loses it loses every server they had configured and
every key they had imported.

So the rule for this project is simple and absolute:

> **User data is never dropped to make a schema change compile.**

**Status.** No database exists yet. There is no `@Database` class, no entity,
and no `app/schemas` directory. The build is already wired for this policy,
and the policy applies from the first migration onwards. Everything below
describes what to do, not what has been done.

---

## 1. What the build already does

Two things in `app/build.gradle.kts` support this policy today.

**Schema export.** KSP is given the schema location:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

Every time the database compiles, Room writes a JSON description of the
schema to `app/schemas/<database class name>/<version>.json`.

**Those JSON files are committed.** They are not build output to be ignored.
They are the record of what the schema looked like at each version. Two
reasons:

- a reviewer can see a schema change as a diff, in the PR, without running
  anything;
- `MigrationTestHelper` needs them to create an old database and run a
  migration against it.

`app/schemas` is not in `.gitignore`. Keep it that way.

**Schemas are also test assets:**

```kotlin
sourceSets {
    getByName("androidTest") {
        assets.srcDirs(files("$projectDir/schemas"))
    }
}
```

`MigrationTestHelper` reads schemas from the test APK's assets. Without that
line, migration tests fail at runtime with "Cannot find the schema file in the
assets folder".

### Two gaps to close with the first migration

These are real, and whoever writes the first migration should fix them in the
same PR.

1. **`room-testing` is on the wrong test classpath.**
   `app/build.gradle.kts` declares `testImplementation(libs.androidx.room.testing)`,
   which puts `MigrationTestHelper` on the JVM unit test classpath. But the
   schema assets are registered for `androidTest`. Either add
   `androidTestImplementation(libs.androidx.room.testing)` and write the
   migration test as an instrumented test (the usual approach), or keep it as
   a Robolectric unit test and register the schema directory as a `test`
   asset source too. Pick one and make the build consistent.

2. **CI runs no instrumented tests.** `.github/workflows/ci.yml` runs
   `ktlintCheck`, `detekt`, `testDebugUnitTest` and `assembleDebug`. There is
   no emulator job. If the migration tests are instrumented, they will not run
   on any pull request, which defeats the point. Either add an emulator job
   (for example `reactivecircus/android-emulator-runner`) to CI, or run the
   migration tests under Robolectric so `testDebugUnitTest` picks them up.

Until one of those is done, a migration test can pass locally and CI will not
notice when it breaks.

---

## 2. The policy

1. **Schemas are exported and committed.** Every version bump adds a new JSON
   file under `app/schemas`. A PR that changes an entity but adds no schema
   file has not been built, or has the export switched off. Either way, do not
   merge it.

2. **Every schema change ships an explicit `Migration` object.** Adding a
   column, adding a table, renaming, changing a type, adding an index: all of
   them need a migration from the previous version to the new one. Room's
   `@RenameColumn` and friends via `AutoMigration` are acceptable where they
   genuinely apply, but the resulting schema still gets committed, and an
   auto migration that needs a `AutoMigrationSpec` still needs the same review.

3. **Every migration ships a migration test.** One test per migration, using
   `MigrationTestHelper`. The test creates the database at the old version,
   inserts real looking rows, runs the migration, and asserts the rows are
   still there and correct. A migration with no test is an untested change to
   the one part of the app that can destroy user data.

4. **`fallbackToDestructiveMigration()` is banned. On every build type.**
   So are `fallbackToDestructiveMigrationOnDowngrade()`,
   `fallbackToDestructiveMigrationFrom(...)` and any hand rolled equivalent
   such as "catch the exception and delete the database file".

   Not in release. Not in debug. Not "just for now while I iterate".

   The reason it is banned in debug too: debug is where it gets added, and
   it is a one line change that then survives a copy and paste into the
   shared builder, or gets left in when the developer's own device is the one
   that quietly wipes. If iterating during development is painful, uninstall
   the app or clear its data by hand. That is a deliberate act on your own
   device. A destructive fallback is an automatic act on everyone's device.

5. **A reviewer must reject any PR that adds a destructive fallback.** This
   is not a discussion to have per PR. If it appears in a diff, the review is
   "changes requested" with a link to this section. The pull request template
   already carries the checkbox; a ticked checkbox next to a diff that adds
   the call is a reason to reject, not to negotiate.

   A grep is worth adding to the `static` CI job so the rule does not depend
   on a human noticing. That check is **not written yet**. Suggested form:

   ```bash
   if grep -rn "fallbackToDestructiveMigration" app/src --include="*.kt"; then
     echo "::error::Destructive migration fallback is banned. See docs/migrations.md"
     exit 1
   fi
   ```

6. **Never edit a released schema JSON.** Once a version has shipped in a tag,
   its schema file describes what is on real devices. Changing it makes the
   migration tests lie. Bump the version and add a new migration instead.

7. **Never renumber or reuse a version.** Version numbers only go up.

8. **Do not delete a column just because nothing reads it any more.** Dropping
   a column in SQLite means recreating the table, which is a real migration
   with a real risk of data loss. If the column is merely unused, leave it and
   note it. Remove it only when there is a reason, and then with a tested
   migration.

---

## 3. Writing a migration, step by step

The example: adding a nullable `notes` column to the `hosts` table, moving the
database from version 3 to version 4.

### Step 1: change the entity

```kotlin
@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val label: String,
    val hostname: String,
    val port: Int,
    val notes: String? = null,   // new
)
```

### Step 2: bump the database version

```kotlin
@Database(
    entities = [HostEntity::class, /* ... */],
    version = 4,
    exportSchema = true,
)
abstract class PulseSshDatabase : RoomDatabase() { /* ... */ }
```

`exportSchema` must stay `true`. It is the default, but state it, so nobody
turns it off by accident.

### Step 3: write the migration

Put it in `data/db/migrations/`, one object per file, named for the pair of
versions it joins.

```kotlin
// data/db/migrations/Migration3To4.kt
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE hosts ADD COLUMN notes TEXT")
    }
}
```

Points to get right:

- The SQL must match what Room expects for version 4 exactly, including
  nullability and default values. Room verifies the schema at open time and
  throws `IllegalStateException: Migration didn't properly handle ...` if it
  does not match. Read the generated `4.json` and compare.
- A nullable Kotlin `String?` maps to a column with no `NOT NULL`. A non
  nullable `String` needs `NOT NULL DEFAULT ''` or the migration will not
  match.
- For anything more complicated than adding a column, SQLite requires the
  create, copy, drop, rename dance:

  ```kotlin
  db.execSQL("CREATE TABLE hosts_new (...)")
  db.execSQL("INSERT INTO hosts_new (id, label, ...) SELECT id, label, ... FROM hosts")
  db.execSQL("DROP TABLE hosts")
  db.execSQL("ALTER TABLE hosts_new RENAME TO hosts")
  db.execSQL("CREATE INDEX index_hosts_label ON hosts (label)")
  ```

  Recreate the indices afterwards. Dropping the table drops them.
- Room wraps `migrate()` in a transaction. Do not open your own.

### Step 4: register it

```kotlin
Room.databaseBuilder(context, PulseSshDatabase::class.java, DB_NAME)
    .openHelperFactory(sqlCipherFactory)
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
```

Keep every migration registered forever. A user coming from a two year old
install needs the whole chain.

### Step 5: build, so the schema is exported

```bash
./gradlew :app:assembleDebug
```

Then confirm `app/schemas/com.pulsessh.app.data.db.PulseSshDatabase/4.json`
exists, and `git add` it. If it did not appear, the KSP argument or
`exportSchema` is wrong.

### Step 6: write the migration test

```kotlin
@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PulseSshDatabase::class.java,
    )

    @Test
    fun migrate3To4_keepsExistingHosts() {
        // 1. Create the database at the OLD version and put real data in it.
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO hosts (id, label, hostname, port)
                VALUES ('h1', 'prod web', 'example.com', 22)
                """.trimIndent(),
            )
        }

        // 2. Run the migration. validateDroppedTables = true makes Room compare
        //    the result against the committed 4.json.
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        // 3. Assert the old row survived and the new column is there.
        db.query("SELECT id, label, hostname, port, notes FROM hosts").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(c.getColumnIndexOrThrow("label"))).isEqualTo("prod web")
            assertThat(c.getInt(c.getColumnIndexOrThrow("port"))).isEqualTo(22)
            assertThat(c.isNull(c.getColumnIndexOrThrow("notes"))).isTrue()
        }
    }
}
```

What makes a migration test worth having:

- **Insert data before migrating.** A test that migrates an empty database
  proves only that the SQL parses. The whole risk is data loss.
- **Insert data that is awkward.** A null in a nullable column, a very long
  string, a unicode label, a row that violates the new constraint you are
  about to add. Those are the rows that break in production.
- **Assert values, not just row counts.** A migration that copies columns in
  the wrong order keeps the count and ruins the data.
- **Let `runMigrationsAndValidate` do the schema check.** Passing `true` for
  `validateDroppedTables` is what catches "the SQL ran but the result does not
  match what Room expects".
- **Also test the full chain occasionally.** A separate test that creates the
  database at version 1 and runs every migration up to the current version
  catches migrations that work individually but not in sequence.

### Step 7: test the encrypted path too

The production database is opened through SQLCipher, not plain SQLite.
`MigrationTestHelper` uses the framework helper by default. At least one test
should run the migration through the same `SupportSQLiteOpenHelper.Factory`
the app uses, with a fixed test passphrase, so that an incompatibility between
SQLCipher and a migration is caught here and not on a user's phone.
`MigrationTestHelper` accepts a factory argument for this.

### Step 8: check the PR

- new `Migration` object, in its own file, in `data/db/migrations/`;
- new schema JSON, committed;
- new migration test, and it fails if you comment out the `execSQL`;
- migration registered in `addMigrations(...)`;
- no `fallbackToDestructiveMigration` anywhere;
- the PR body says what changed in the schema and why.

---

## 4. If a migration turns out to be broken after release

Assume "broken" means it either loses data, or throws and leaves users unable
to open the app.

**1. Stop the spread.**
If the release is on Google Play, halt the staged rollout immediately. That
stops new users receiving it. Users who already updated are not helped by
this, but it caps the blast radius.

**2. Do not roll the version number back.**
Room only migrates upward. Shipping an APK with a lower database version to a
device that already migrated produces a downgrade, which is exactly the
situation `fallbackToDestructiveMigrationOnDowngrade` exists to paper over,
and that call is banned for good reason. Always fix forward.

**3. Work out what the affected devices actually contain.**
There are usually three populations, and they need different handling:

| Population | State |
| --- | --- |
| Never updated | Still on the old version. Safe if the new release is pulled. |
| Updated, migration succeeded but data is wrong | The dangerous one. The database opens, so nothing looks broken, but rows are corrupt or missing. |
| Updated, migration threw | The app crashes on open. Visible, but the data on disk is usually still intact, because Room's migration runs in a transaction that rolls back. |

**4. Write a repair migration, not an edit.**
Bump to the next version and add a new migration that repairs the damage. It
has to be safe to run on a database that was never damaged, because you cannot
tell the populations apart from inside `migrate()` without checking. Detect
the damaged shape (a missing column, a null where there should not be one, a
sentinel value the bad migration wrote) and repair only that.

**5. Reproduce first, then fix.**
Add a migration test that starts from the *damaged* state and asserts the
repair. That test is the proof the fix works. Do not ship a repair you have
only reasoned about.

**6. If the data is genuinely unrecoverable, say so.**
Do not silently wipe and hope nobody notices. Ship a build that detects the
broken state, tells the user in plain language what was lost and what was
kept, and lets them start again deliberately. For PulseSSH that means telling
them which hosts and keys need re adding. A user who knows what they lost can
recover. A user whose host list quietly emptied cannot.

**7. Write the post mortem into this file.**
Add a short entry: what the migration did, why the test did not catch it,
what changed in the process. The point is the last part. A migration bug that
reaches release is nearly always a testing gap, and the testing gap is the
thing to fix.

### Post mortems

None yet.
