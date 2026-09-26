import type {CodegenConfig} from "@graphql-codegen/cli";

const config: CodegenConfig = {
    schema: "../backend/src/main/resources/graphql/schema.graphqls",
    documents: ["src/operations.graphql"],
    generates: {
        "src/gql/": {
            preset: "client",
            presetConfig: {fragmentMasking: false},
            config: {useTypeImports: true, enumType: "enum"},
        },
    },
};
export default config;
