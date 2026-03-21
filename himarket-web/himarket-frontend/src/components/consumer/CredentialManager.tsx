import { AuthConfig } from "./AuthConfig";

interface CredentialManagerProps {
  consumerId: string;
}

export function CredentialManager({ consumerId }: CredentialManagerProps) {
  return <AuthConfig consumerId={consumerId} />;
}
