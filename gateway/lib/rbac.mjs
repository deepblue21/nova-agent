// Role-based access control — pure permission logic (no DB), so it is fully
// unit-testable. Workspace roles, ranked: admin > editor > viewer.
//
// Actions:
//   read   — view workspace + its shared resources
//   write  — create/update/delete shared resources (e.g. knowledge docs)
//   manage — manage members, roles, workspace settings, delete workspace

export const ROLES = ["viewer", "editor", "admin"];
const RANK = { viewer: 1, editor: 2, admin: 3 };

const ALLOWED = {
  read:   { viewer: true, editor: true, admin: true },
  write:  { viewer: false, editor: true, admin: true },
  manage: { viewer: false, editor: false, admin: true },
};

export function isRole(role) {
  return typeof role === "string" && Object.prototype.hasOwnProperty.call(RANK, role);
}

// Can a member with `role` perform `action`? Unknown role/action → false.
export function can(role, action) {
  const a = ALLOWED[action];
  return !!(a && role && a[role] === true);
}

// True if `role` is at least as privileged as `min`.
export function roleAtLeast(role, min) {
  return isRole(role) && isRole(min) && RANK[role] >= RANK[min];
}

// Highest-privilege role from a list (null if none valid). Useful when a user
// has multiple membership rows.
export function topRole(roles) {
  let best = null;
  for (const r of roles || []) {
    if (isRole(r) && (!best || RANK[r] > RANK[best])) best = r;
  }
  return best;
}
