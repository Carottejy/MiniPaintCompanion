// MiniPaintCompanion : amis et schémas partagés.
// Pas de compte : chaque téléphone génère un secret aléatoire (en-tête x-secret) ; seul son
// empreinte SHA-256 est stockée. Un code ami public (ex. MPC-7K4QXZ) sert à s'ajouter.
// Les tables mpc_* ne sont accessibles qu'ici (service role), jamais directement par l'appli.
import { createClient } from "npm:@supabase/supabase-js@2";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});
const BUCKET = "mpc-photos";
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const clean = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");

async function sha256(s: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(d)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function newCode(): string {
  const r = crypto.getRandomValues(new Uint8Array(6));
  return "MPC-" + Array.from(r).map((b) => ALPHABET[b % ALPHABET.length]).join("");
}
const normCode = (v: unknown) => clean(v, 20).toUpperCase().replace(/\s/g, "").replace(/^(MPC)?-?/, "MPC-");

// Limite simple par adresse IP : 120 requêtes / minute.
const hits = new Map<string, number[]>();
function limited(ip: string): boolean {
  const now = Date.now();
  const recent = (hits.get(ip) ?? []).filter((t) => now - t < 60_000);
  recent.push(now);
  hits.set(ip, recent);
  return recent.length > 120;
}

type User = { id: string; code: string; pseudo: string };

async function userByCode(code: string): Promise<User | null> {
  const { data } = await db.from("mpc_users").select("id,code,pseudo").eq("code", code).maybeSingle();
  return data;
}
async function link(a: string, b: string) {
  const { data } = await db.from("mpc_friendships").select("requester,addressee,accepted")
    .or(`and(requester.eq.${a},addressee.eq.${b}),and(requester.eq.${b},addressee.eq.${a})`);
  return data ?? [];
}
async function areFriends(a: string, b: string) {
  return (await link(a, b)).some((l) => l.accepted);
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "method" });
  if (req.headers.get("x-app") !== "MiniPaintCompanion") return json(403, { error: "app" });
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "?";
  if (limited(ip)) return json(429, { error: "Trop de requêtes, réessaie dans une minute." });

  const secret = req.headers.get("x-secret") ?? "";
  if (secret.length < 32 || secret.length > 128) return json(401, { error: "Identité manquante." });
  const hash = await sha256(secret);

  let b: Record<string, unknown>;
  try { b = await req.json(); } catch { return json(400, { error: "json" }); }
  const action = clean(b.action, 20);

  // --- Identité ---
  let { data: me } = await db.from("mpc_users").select("id,code,pseudo").eq("secret_hash", hash).maybeSingle();
  if (action === "hello") {
    const pseudo = clean(b.pseudo, 40);
    if (!me) {
      if (!pseudo) return json(400, { error: "Choisis un pseudo." });
      for (let i = 0; i < 5 && !me; i++) {
        const { data, error } = await db.from("mpc_users")
          .insert({ secret_hash: hash, code: newCode(), pseudo }).select("id,code,pseudo").single();
        if (!error) me = data;
      }
      if (!me) return json(500, { error: "Création du profil impossible." });
    } else if (pseudo && pseudo !== me.pseudo) {
      await db.from("mpc_users").update({ pseudo, updated_at: new Date().toISOString() }).eq("id", me.id);
      me.pseudo = pseudo;
    }
    return json(200, { code: me.code, pseudo: me.pseudo });
  }
  if (!me) return json(401, { error: "Profil inconnu : choisis d'abord un pseudo." });

  switch (action) {
    case "friends": {
      const { data: links } = await db.from("mpc_friendships").select("requester,addressee,accepted")
        .or(`requester.eq.${me.id},addressee.eq.${me.id}`);
      const otherIds = (links ?? []).map((l) => (l.requester === me!.id ? l.addressee : l.requester));
      if (otherIds.length === 0) return json(200, { friends: [] });
      const { data: users } = await db.from("mpc_users").select("id,code,pseudo").in("id", otherIds);
      const { data: counts } = await db.from("mpc_schemas").select("owner").in("owner", otherIds);
      const byId = new Map((users ?? []).map((u) => [u.id, u]));
      const friends = (links ?? []).map((l) => {
        const other = l.requester === me!.id ? l.addressee : l.requester;
        const u = byId.get(other);
        return {
          code: u?.code ?? "", pseudo: u?.pseudo ?? "?",
          status: l.accepted ? "friend" : l.requester === me!.id ? "outgoing" : "incoming",
          schemaCount: l.accepted ? (counts ?? []).filter((c) => c.owner === other).length : 0,
        };
      }).sort((a, b) => a.pseudo.localeCompare(b.pseudo));
      return json(200, { friends });
    }
    case "add": {
      const other = await userByCode(normCode(b.code));
      if (!other) return json(404, { error: "Aucun utilisateur avec ce code." });
      if (other.id === me.id) return json(400, { error: "C'est ton propre code." });
      const existing = await link(me.id, other.id);
      if (existing.some((l) => l.accepted)) return json(200, { status: "friend", pseudo: other.pseudo });
      // Il m'avait déjà invité : on accepte directement.
      if (existing.some((l) => l.requester === other.id)) {
        await db.from("mpc_friendships").update({ accepted: true }).eq("requester", other.id).eq("addressee", me.id);
        return json(200, { status: "friend", pseudo: other.pseudo });
      }
      if (existing.length === 0) await db.from("mpc_friendships").insert({ requester: me.id, addressee: other.id });
      return json(200, { status: "outgoing", pseudo: other.pseudo });
    }
    case "accept": {
      const other = await userByCode(normCode(b.code));
      if (!other) return json(404, { error: "Utilisateur introuvable." });
      await db.from("mpc_friendships").update({ accepted: true }).eq("requester", other.id).eq("addressee", me.id);
      return json(200, { status: "friend" });
    }
    case "remove": {
      const other = await userByCode(normCode(b.code));
      if (!other) return json(200, { ok: true });
      await db.from("mpc_friendships").delete()
        .or(`and(requester.eq.${me.id},addressee.eq.${other.id}),and(requester.eq.${other.id},addressee.eq.${me.id})`);
      return json(200, { ok: true });
    }
    case "publish": {
      const projectId = clean(b.projectId, 80);
      const schema = b.schema;
      if (!projectId || typeof schema !== "object" || schema === null) return json(400, { error: "Schéma invalide." });
      if (JSON.stringify(schema).length > 400_000) return json(413, { error: "Schéma trop volumineux." });
      const path = `${me.id}/${projectId.replace(/[^A-Za-z0-9_-]/g, "_")}.jpg`;
      let photoPath: string | null | undefined = undefined; // undefined = inchangé
      if (typeof b.photo === "string" && b.photo.length > 0) {
        if (b.photo.length > 4_000_000) return json(413, { error: "Photo trop lourde." });
        const bytes = Uint8Array.from(atob(b.photo), (c) => c.charCodeAt(0));
        const { error } = await db.storage.from(BUCKET).upload(path, bytes, { contentType: "image/jpeg", upsert: true });
        if (error) return json(500, { error: "Envoi de la photo impossible." });
        photoPath = path;
      } else if (b.photo === null) {
        await db.storage.from(BUCKET).remove([path]);
        photoPath = null;
      }
      const row: Record<string, unknown> = {
        owner: me.id, project_id: projectId, name: clean(b.name, 120), payload: schema,
        updated_at: new Date().toISOString(),
      };
      if (photoPath !== undefined) row.photo_path = photoPath;
      const { error } = await db.from("mpc_schemas").upsert(row);
      if (error) return json(500, { error: "Publication impossible." });
      return json(200, { ok: true });
    }
    case "unpublish": {
      const projectId = clean(b.projectId, 80);
      const { data } = await db.from("mpc_schemas").select("photo_path").eq("owner", me.id).eq("project_id", projectId).maybeSingle();
      if (data?.photo_path) await db.storage.from(BUCKET).remove([data.photo_path]);
      await db.from("mpc_schemas").delete().eq("owner", me.id).eq("project_id", projectId);
      return json(200, { ok: true });
    }
    case "mine": {
      const { data } = await db.from("mpc_schemas").select("project_id,updated_at").eq("owner", me.id);
      return json(200, { schemas: (data ?? []).map((s) => ({ projectId: s.project_id, updatedAt: s.updated_at })) });
    }
    case "schemas": {
      const other = await userByCode(normCode(b.code));
      if (!other || !(await areFriends(me.id, other.id))) return json(403, { error: "Vous n'êtes pas amis." });
      const { data } = await db.from("mpc_schemas").select("project_id,name,payload,photo_path,updated_at")
        .eq("owner", other.id).order("updated_at", { ascending: false });
      const rows = data ?? [];
      const paths = rows.map((r) => r.photo_path).filter((p): p is string => !!p);
      const urls = new Map<string, string>();
      if (paths.length) {
        const { data: signed } = await db.storage.from(BUCKET).createSignedUrls(paths, 3600);
        (signed ?? []).forEach((s) => { if (s.path && s.signedUrl) urls.set(s.path, s.signedUrl); });
      }
      return json(200, {
        pseudo: other.pseudo,
        schemas: rows.map((r) => ({
          projectId: r.project_id, name: r.name, schema: r.payload, updatedAt: r.updated_at,
          photoUrl: r.photo_path ? urls.get(r.photo_path) ?? null : null,
        })),
      });
    }
    case "delete_me": {
      const { data: files } = await db.from("mpc_schemas").select("photo_path").eq("owner", me.id);
      const paths = (files ?? []).map((f) => f.photo_path).filter((p): p is string => !!p);
      if (paths.length) await db.storage.from(BUCKET).remove(paths);
      await db.from("mpc_users").delete().eq("id", me.id);
      return json(200, { ok: true });
    }
  }
  return json(400, { error: "Action inconnue." });
});
