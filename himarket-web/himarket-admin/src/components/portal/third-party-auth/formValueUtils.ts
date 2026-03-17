export type ThirdPartyAuthFormValues = Record<string, unknown>;

export function formatCommaSeparated(values?: string[]) {
  return (values || []).join(", ");
}

export function parseCommaSeparated(value?: string) {
  return String(value || "")
    .split(",")
    .map(item => item.trim())
    .filter(Boolean);
}

export function formatJsonObject(value?: Record<string, string>) {
  return value ? JSON.stringify(value, null, 2) : "";
}

export function formatJsonArrayObject(value?: Record<string, string[]>) {
  return value ? JSON.stringify(value, null, 2) : "";
}

export function parseStringMap(value?: string) {
  if (!value || !String(value).trim()) {
    return undefined;
  }
  const parsed = JSON.parse(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("HTTP Request Headers 必须是 JSON 对象");
  }
  return Object.entries(parsed).reduce<Record<string, string>>(
    (result, [key, item]) => {
      if (typeof item === "string" && key.trim()) {
        result[key] = item;
      }
      return result;
    },
    {}
  );
}

export function parseStringArrayMap(value?: string, fieldLabel?: string) {
  if (!value || !String(value).trim()) {
    return undefined;
  }
  const parsed = JSON.parse(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error(`${fieldLabel || "属性规则"}必须是 JSON 对象`);
  }
  return Object.entries(parsed).reduce<Record<string, string[]>>(
    (result, [key, item]) => {
      if (!key.trim()) {
        return result;
      }
      if (Array.isArray(item)) {
        const values = item
          .map(entry => String(entry || "").trim())
          .filter(Boolean);
        if (values.length > 0) {
          result[key] = values;
        }
        return result;
      }
      if (typeof item === "string" && item.trim()) {
        result[key] = [item.trim()];
      }
      return result;
    },
    {}
  );
}

export function parseServiceContacts(value?: string) {
  if (!value || !String(value).trim()) {
    return undefined;
  }
  const parsed = JSON.parse(value);
  if (!Array.isArray(parsed)) {
    throw new Error("Service Contacts 必须是 JSON 数组");
  }
  return parsed
    .filter(item => item && typeof item === "object" && !Array.isArray(item))
    .map(item => ({
      name: typeof item.name === "string" ? item.name : undefined,
      email: typeof item.email === "string" ? item.email : undefined,
      phone: typeof item.phone === "string" ? item.phone : undefined,
      department:
        typeof item.department === "string" ? item.department : undefined,
      type: typeof item.type === "string" ? item.type : undefined,
    }))
    .filter(
      item => item.name || item.email || item.phone || item.department || item.type
    );
}

export function stringifyServiceContacts(value?: unknown[]) {
  return JSON.stringify(value || [], null, 2);
}

export function parseOptionalNumber(value?: string | number) {
  if (value === undefined || value === null || value === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}
