#!/usr/bin/env python3
"""
One-time migration script: MongoDB → PostgreSQL

Usage:
    pip install pymongo psycopg2-binary
    python db/migrate_mongo_to_postgres.py \
        --mongo-uri mongodb://localhost:27017/simulator \
        --pg-dsn    "host=localhost dbname=simulator user=simulator password=simulator"

The script is fully idempotent: it uses INSERT ... ON CONFLICT (id) DO UPDATE,
so it is safe to re-run and will always overwrite PostgreSQL rows with MongoDB data.
"""

import argparse
import json
import sys
from datetime import datetime, timezone

import pymongo
import psycopg2
import psycopg2.extras


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def ts(value):
    """Convert a MongoDB datetime or None to a timezone-aware datetime string."""
    if value is None:
        return None
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        return value.isoformat()
    return str(value)


def to_jsonb(value):
    """Serialize a Python object to a JSON string for JSONB columns."""
    if value is None:
        return None
    return json.dumps(value, default=str)


def oid_to_str(value):
    """Convert a MongoDB ObjectId or any value to a plain string."""
    if value is None:
        return None
    return str(value)


# ---------------------------------------------------------------------------
# Collection → table migrations
# ---------------------------------------------------------------------------

def migrate_mappings(mongo_db, pg_conn):
    collection = mongo_db["mappings"]
    cursor = pg_conn.cursor()
    total = 0

    for doc in collection.find():
        row_id = oid_to_str(doc.get("_id"))
        cursor.execute(
            """
            INSERT INTO mappings
                (id, name, namespace, priority, endpoint_type, enabled,
                 tags, created_at, updated_at, deleted, request, response,
                 delays, graphql_spec)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (id) DO UPDATE SET
                name          = EXCLUDED.name,
                namespace     = EXCLUDED.namespace,
                priority      = EXCLUDED.priority,
                endpoint_type = EXCLUDED.endpoint_type,
                enabled       = EXCLUDED.enabled,
                tags          = EXCLUDED.tags,
                created_at    = EXCLUDED.created_at,
                updated_at    = EXCLUDED.updated_at,
                deleted       = EXCLUDED.deleted,
                request       = EXCLUDED.request,
                response      = EXCLUDED.response,
                delays        = EXCLUDED.delays,
                graphql_spec  = EXCLUDED.graphql_spec
            """,
            (
                row_id,
                doc.get("name"),
                doc.get("namespace"),
                doc.get("priority"),
                doc.get("endpointType"),
                doc.get("enabled", True),
                to_jsonb(doc.get("tags")),
                ts(doc.get("createdAt")),
                ts(doc.get("updatedAt")),
                doc.get("deleted", False),
                to_jsonb(doc.get("request")),
                to_jsonb(doc.get("response")),
                to_jsonb(doc.get("delays")),
                to_jsonb(doc.get("graphQLSpec")),
            ),
        )
        total += 1

    pg_conn.commit()
    print(f"  mappings: {total} rows upserted")


def migrate_user_profiles(mongo_db, pg_conn):
    collection = mongo_db["userProfiles"]
    cursor = pg_conn.cursor()
    total = 0

    for doc in collection.find():
        row_id = oid_to_str(doc.get("_id"))
        cursor.execute(
            """
            INSERT INTO api_user_profiles
                (id, user_id, email, first_name, last_name, password_hash,
                 namespaces, default_namespace, created_at, last_login,
                 active, deleted)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (user_id) DO UPDATE SET
                id                = EXCLUDED.id,
                email             = EXCLUDED.email,
                first_name        = EXCLUDED.first_name,
                last_name         = EXCLUDED.last_name,
                password_hash     = EXCLUDED.password_hash,
                namespaces        = EXCLUDED.namespaces,
                default_namespace = EXCLUDED.default_namespace,
                created_at        = EXCLUDED.created_at,
                last_login        = EXCLUDED.last_login,
                active            = EXCLUDED.active,
                deleted           = EXCLUDED.deleted
            """,
            (
                row_id,
                doc.get("userId"),
                doc.get("email"),
                doc.get("firstName"),
                doc.get("lastName"),
                doc.get("passwordHash"),
                to_jsonb(doc.get("namespaces", [])),
                doc.get("defaultNamespace"),
                ts(doc.get("createdAt")),
                ts(doc.get("lastLogin")),
                doc.get("active", True),
                doc.get("deleted", False),
            ),
        )
        total += 1

    pg_conn.commit()
    print(f"  api_user_profiles: {total} rows upserted")


def migrate_namespaces(mongo_db, pg_conn):
    collection = mongo_db["namespaces"]
    cursor = pg_conn.cursor()
    total = 0

    for doc in collection.find():
        row_id = oid_to_str(doc.get("_id"))
        cursor.execute(
            """
            INSERT INTO namespaces
                (id, name, display_name, description, members, owner,
                 created_at, active, deleted)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (name) DO UPDATE SET
                id           = EXCLUDED.id,
                display_name = EXCLUDED.display_name,
                description  = EXCLUDED.description,
                members      = EXCLUDED.members,
                owner        = EXCLUDED.owner,
                created_at   = EXCLUDED.created_at,
                active       = EXCLUDED.active,
                deleted      = EXCLUDED.deleted
            """,
            (
                row_id,
                doc.get("name"),
                doc.get("displayName"),
                doc.get("description"),
                to_jsonb(doc.get("members", [])),
                doc.get("owner"),
                ts(doc.get("createdAt")),
                doc.get("active", True),
                doc.get("deleted", False),
            ),
        )
        total += 1

    pg_conn.commit()
    print(f"  namespaces: {total} rows upserted")


def migrate_datasets(mongo_db, pg_conn):
    collection = mongo_db["datasets"]
    cursor = pg_conn.cursor()
    total = 0

    for doc in collection.find():
        row_id = oid_to_str(doc.get("_id"))
        cursor.execute(
            """
            INSERT INTO datasets
                (id, name, description, active, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (id) DO UPDATE SET
                name        = EXCLUDED.name,
                description = EXCLUDED.description,
                active      = EXCLUDED.active,
                created_at  = EXCLUDED.created_at,
                updated_at  = EXCLUDED.updated_at
            """,
            (
                row_id,
                doc.get("name"),
                doc.get("description"),
                doc.get("active", True),
                ts(doc.get("createdAt")),
                ts(doc.get("updatedAt")),
            ),
        )
        total += 1

    pg_conn.commit()
    print(f"  datasets: {total} rows upserted")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Migrate MongoDB data to PostgreSQL")
    parser.add_argument(
        "--mongo-uri",
        default="mongodb://localhost:27017/simulator",
        help="MongoDB connection URI (default: mongodb://localhost:27017/simulator)",
    )
    parser.add_argument(
        "--pg-dsn",
        default="host=localhost dbname=simulator user=simulator password=simulator",
        help="PostgreSQL DSN string",
    )
    args = parser.parse_args()

    print(f"Connecting to MongoDB: {args.mongo_uri}")
    mongo_client = pymongo.MongoClient(args.mongo_uri)
    # Extract DB name from URI (last path segment)
    db_name = args.mongo_uri.rstrip("/").rsplit("/", 1)[-1]
    mongo_db = mongo_client[db_name]

    print(f"Connecting to PostgreSQL: {args.pg_dsn}")
    pg_conn = psycopg2.connect(args.pg_dsn)
    psycopg2.extras.register_default_jsonb(pg_conn)

    print("Starting migration...")
    try:
        migrate_mappings(mongo_db, pg_conn)
        migrate_user_profiles(mongo_db, pg_conn)
        migrate_namespaces(mongo_db, pg_conn)
        migrate_datasets(mongo_db, pg_conn)
        print("Migration complete.")
    except Exception as exc:
        pg_conn.rollback()
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        pg_conn.close()
        mongo_client.close()


if __name__ == "__main__":
    main()
