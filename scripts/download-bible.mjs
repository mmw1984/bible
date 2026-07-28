import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises'

const API = 'https://bible-api.com/data'
const OUTPUT = new URL('../public/bible/', import.meta.url)
const translations = (process.env.TRANSLATIONS || 'cuv,web')
  .split(',')
  .map(value => value.trim().toLowerCase())
  .filter(Boolean)
// bible-api.com's public endpoint permits roughly 15 requests per 30 seconds.
const REQUEST_INTERVAL_MS = Number(process.env.REQUEST_INTERVAL_MS || 2200)
const EXPECTED_BOOKS = 66
const EXPECTED_CHAPTERS = 1189

let lastRequestAt = 0

const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))

async function getJson(url, attempt = 1) {
  const pacingDelay = Math.max(0, REQUEST_INTERVAL_MS - (Date.now() - lastRequestAt))
  if (pacingDelay) await sleep(pacingDelay)
  lastRequestAt = Date.now()

  let response
  try {
    response = await fetch(url, { headers: { accept: 'application/json' } })
  } catch (error) {
    if (attempt >= 12) throw error
    const delay = Math.min(30_000, attempt * 3_000)
    console.log(`[retry ${attempt}] network error; waiting ${Math.ceil(delay / 1000)}s`)
    await sleep(delay)
    return getJson(url, attempt + 1)
  }

  if (response.ok) return response.json()
  if (attempt >= 12) throw new Error(`${response.status} ${url}`)
  const retryAfter = Number(response.headers.get('retry-after'))
  const delay = Number.isFinite(retryAfter) && retryAfter > 0
    ? retryAfter * 1000
    : Math.min(30_000, attempt * 3_000)
  console.log(`[retry ${attempt}] ${response.status}; waiting ${Math.ceil(delay / 1000)}s`)
  await sleep(delay)
  return getJson(url, attempt + 1)
}

async function readJson(url) {
  try {
    return JSON.parse(await readFile(url, 'utf8'))
  } catch {
    return null
  }
}

async function writeJsonAtomic(url, value) {
  const temporaryUrl = new URL(`${url.pathname}.tmp`, url)
  await writeFile(temporaryUrl, JSON.stringify(value))
  await rename(temporaryUrl, url)
}

function chapterIsComplete(verses) {
  return Array.isArray(verses) && verses.length > 0 && verses.every(verse =>
    // Some translations retain an omitted verse as an empty string so that
    // subsequent array indexes still match their printed verse numbers.
    typeof verse === 'string'
  )
}

await mkdir(OUTPUT, { recursive: true })

for (const translation of translations) {
  const catalog = await getJson(`${API}/${translation}`)
  if (catalog.books.length !== EXPECTED_BOOKS) {
    throw new Error(`[${translation}] expected ${EXPECTED_BOOKS} books, got ${catalog.books.length}`)
  }

  const translationDir = new URL(`./${translation}/`, OUTPUT)
  await mkdir(translationDir, { recursive: true })
  console.log(`[${translation}] ${catalog.books.length} books`)
  const manifest = { translation: catalog.translation, books: [] }

  for (const [bookIndex, book] of catalog.books.entries()) {
    const details = await getJson(`${API}/${translation}/${book.id}`)
    const bookUrl = new URL(`./${book.id}.json`, translationDir)
    const savedBook = await readJson(bookUrl)
    const localBook = savedBook?.id === book.id
      ? savedBook
      : {
          id: book.id,
          name: book.name,
          translation: catalog.translation.identifier,
          chapters: {},
        }

    for (const chapter of details.chapters) {
      const chapterNumber = String(chapter.chapter)
      if (chapterIsComplete(localBook.chapters[chapterNumber])) continue

      const payload = await getJson(`${API}/${translation}/${book.id}/${chapter.chapter}`)
      const verses = payload.verses.map(verse => verse.text)
      if (!chapterIsComplete(verses)) {
        throw new Error(`[${translation}] ${book.id} ${chapter.chapter} is empty or incomplete`)
      }
      localBook.chapters[chapterNumber] = verses
      // Save every chapter so an interrupted run resumes without redownloading it.
      await writeJsonAtomic(bookUrl, localBook)
    }

    const chapterCount = Object.keys(localBook.chapters).length
    if (chapterCount !== details.chapters.length) {
      throw new Error(`[${translation}] ${book.id}: expected ${details.chapters.length} chapters, got ${chapterCount}`)
    }
    await writeJsonAtomic(bookUrl, localBook)
    manifest.books.push({ id: book.id, name: book.name, chapters: details.chapters.length })
    console.log(`[${translation}] ${bookIndex + 1}/${catalog.books.length} ${book.id} (${details.chapters.length})`)
  }

  const totalChapters = manifest.books.reduce((sum, book) => sum + book.chapters, 0)
  if (totalChapters !== EXPECTED_CHAPTERS) {
    throw new Error(`[${translation}] expected ${EXPECTED_CHAPTERS} chapters, got ${totalChapters}`)
  }
  await writeJsonAtomic(new URL('./manifest.json', translationDir), manifest)

  let verseCount = 0
  for (const book of manifest.books) {
    const payload = await readJson(new URL(`./${book.id}.json`, translationDir))
    for (let chapter = 1; chapter <= book.chapters; chapter += 1) {
      const verses = payload?.chapters?.[String(chapter)]
      if (!chapterIsComplete(verses)) {
        throw new Error(`[${translation}] verification failed at ${book.id} ${chapter}`)
      }
      verseCount += verses.length
    }
  }
  const bytes = (await Promise.all(manifest.books.map(book =>
    stat(new URL(`./${book.id}.json`, translationDir)).then(file => file.size)
  ))).reduce((sum, size) => sum + size, 0) + (await stat(new URL('./manifest.json', translationDir))).size
  console.log(`[${translation}] VERIFIED ${EXPECTED_BOOKS} books, ${totalChapters} chapters, ${verseCount} verses, ${bytes} bytes`)
}

console.log('Bible download complete.')
