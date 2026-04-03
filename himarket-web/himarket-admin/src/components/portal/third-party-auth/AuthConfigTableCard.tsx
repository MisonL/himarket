import { Table, type TableProps } from "antd";
import { ThirdPartyAuthConfig } from "@/types";

interface AuthConfigTableCardProps {
  title: string;
  description: string;
  emptyText: string;
  columns: NonNullable<TableProps<ThirdPartyAuthConfig>["columns"]>;
  dataSource: ThirdPartyAuthConfig[];
}

export function AuthConfigTableCard({
  title,
  description,
  emptyText,
  columns,
  dataSource,
}: AuthConfigTableCardProps) {
  return (
    <div className="bg-white rounded-lg">
      <div className="py-4 border-b border-gray-200">
        <h4 className="text-lg font-medium text-gray-900">{title}</h4>
        <p className="text-sm text-gray-500 mt-1">{description}</p>
      </div>
      <Table
        columns={columns}
        dataSource={dataSource}
        rowKey="provider"
        pagination={false}
        size="small"
        scroll={{ x: 880 }}
        locale={{
          emptyText,
        }}
      />
    </div>
  );
}
