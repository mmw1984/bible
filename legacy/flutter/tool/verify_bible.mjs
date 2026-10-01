import { readFile } from 'node:fs/promises'

const ROOT = new URL('../assets/bible/', import.meta.url)
const TRANSLATIONS = ['cuv', 'web']
const EXPECTED_BOOKS = 66
const EXPECTED_CHAPTERS = 1189

for (const translation of TRANSLATIONS) {
  const directory = new URL(`./${translation}/`, ROOT)
  const manifest = JSON.parse(await readFile(new URL('manifest.json', directory)))
  if (manifest.books.length !== EXPECTED_BOOKS) {
    throw new Error(`[${translation}] expected ${EXPECTED_BOOKS} books`)
  }

  let chapters = 0
  let verses = 0
  for (const book of manifest.books) {
    const payload = JSON.parse(
      await readFile(new URL(`${book.id}.json`, directory), 'utf8'),
    )
    for (let chapter = 1; chapter <= book.chapters; chapter += 1) {
      const content = payload.chapters[String(chapter)]
      if (!Array.isArray(content) || content.length === 0) {
        throw new Error(`[${translation}] missing ${book.id} ${chapter}`)
      }
      verses += content.length
    }
    chapters += book.chapters
  }

  if (chapters !== EXPECTED_CHAPTERS) {
    throw new Error(`[${translation}] expected ${EXPECTED_CHAPTERS} chapters`)
  }
  console.log(
    `[${translation}] verified ${EXPECTED_BOOKS} books, ${chapters} chapters, ${verses} verses`,
  )
}
