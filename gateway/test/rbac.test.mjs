// RBAC permission-matrix tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { can, roleAtLeast, topRole, isRole, ROLES } from "../lib/rbac.mjs";

test("can: read allowed for all roles", () => {
  for (const r of ROLES) assert.equal(can(r, "read"), true);
});

test("can: write only editor + admin", () => {
  assert.equal(can("viewer", "write"), false);
  assert.equal(can("editor", "write"), true);
  assert.equal(can("admin", "write"), true);
});

test("can: manage only admin", () => {
  assert.equal(can("viewer", "manage"), false);
  assert.equal(can("editor", "manage"), false);
  assert.equal(can("admin", "manage"), true);
});

test("can: unknown role/action → false", () => {
  assert.equal(can("owner", "read"), false);
  assert.equal(can("admin", "destroy"), false);
  assert.equal(can(null, "read"), false);
  assert.equal(can(undefined, undefined), false);
});

test("roleAtLeast: rank ordering admin>editor>viewer", () => {
  assert.equal(roleAtLeast("admin", "viewer"), true);
  assert.equal(roleAtLeast("editor", "editor"), true);
  assert.equal(roleAtLeast("viewer", "editor"), false);
  assert.equal(roleAtLeast("bogus", "viewer"), false);
});

test("topRole: highest valid role; null when none", () => {
  assert.equal(topRole(["viewer", "admin", "editor"]), "admin");
  assert.equal(topRole(["viewer", "editor"]), "editor");
  assert.equal(topRole(["bogus", "viewer"]), "viewer");
  assert.equal(topRole(["nope"]), null);
  assert.equal(topRole([]), null);
});

test("isRole: only the three known roles", () => {
  assert.ok(isRole("admin") && isRole("editor") && isRole("viewer"));
  assert.equal(isRole("owner"), false);
  assert.equal(isRole(""), false);
});
