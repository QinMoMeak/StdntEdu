import { readFile, readdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const projectRoot = path.resolve(frontendRoot, '..')
const contract = await readFile(path.join(projectRoot, 'api', 'openapi.yaml'), 'utf8')
const apiSource = await readFile(path.join(frontendRoot, 'src', 'api', 'generated', 'apis', 'DefaultApi.ts'), 'utf8')
const operationIds = [...contract.matchAll(/operationId:\s*([A-Za-z][A-Za-z0-9_]*)/g)].map((match) => match[1])
const missing = operationIds.filter((operationId) => !apiSource.includes(`${operationId}(`))
const modelFiles = (await readdir(path.join(frontendRoot, 'src', 'api', 'generated', 'models'))).filter(
  (file) => file.endsWith('.ts') && file !== 'index.ts',
)
const apiFiles = (await readdir(path.join(frontendRoot, 'src', 'api', 'generated', 'apis'))).filter(
  (file) => file.endsWith('Api.ts'),
)

if (operationIds.length !== 116 || new Set(operationIds).size !== operationIds.length || missing.length) {
  throw new Error(`Generated client mismatch: operations=${operationIds.length}, missing=${missing.join(', ')}`)
}
if (/bearerAuth|Authorization/.test(apiSource)) {
  throw new Error('Generated operations unexpectedly require Bearer authentication')
}

console.log(`Generated client verified: ${apiFiles.length} API class, ${modelFiles.length} models, ${operationIds.length} operations`)
