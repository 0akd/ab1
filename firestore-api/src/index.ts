import { Hono } from 'hono'
import { cors } from 'hono/cors'

const app = new Hono()

app.use('/api/*', cors())

// Firebase project id from absolutra/app/google-services.json
const PROJECT_ID = 'absolutra-61e8b'
const COLLECTION = 'notes'
const SHARED_NOTE_ID = 'shared_note'

type FirestoreValue = {
  stringValue?: string
  timestampValue?: string
}

type FirestoreDocument = {
  name: string
  fields?: {
    text?: FirestoreValue
    timestamp?: FirestoreValue
  }
}

type Note = {
  id: string
  text: string
  timestamp: string
}

function documentUrl(id: string) {
  return `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/${COLLECTION}/${id}`
}

function toNote(doc: FirestoreDocument): Note {
  return {
    id: doc.name.split('/').pop() || SHARED_NOTE_ID,
    text: doc.fields?.text?.stringValue || '',
    timestamp:
      doc.fields?.timestamp?.timestampValue ||
      doc.fields?.timestamp?.stringValue ||
      '',
  }
}

app.get('/', (c) => c.text('firestore-api'))

app.get('/api/notes', async (c) => {
  try {
    const response = await fetch(documentUrl(SHARED_NOTE_ID))

    if (response.status === 404) {
      return c.json([])
    }

    if (!response.ok) {
      const detail = await response.text()
      return c.json(
        { error: 'Failed to fetch data from Firestore', detail },
        response.status as 400 | 401 | 403 | 404 | 500
      )
    }

    const doc = (await response.json()) as FirestoreDocument
    return c.json([toNote(doc)])
  } catch {
    return c.json({ error: 'Internal Server Error' }, 500)
  }
})

app.post('/api/notes', async (c) => {
  let body: { text?: unknown }
  try {
    body = await c.req.json()
  } catch {
    return c.json({ error: 'Expected a JSON body' }, 400)
  }

  if (typeof body.text !== 'string') {
    return c.json({ error: 'text must be a string' }, 400)
  }

  const timestamp = new Date().toISOString()

  try {
    const response = await fetch(documentUrl(SHARED_NOTE_ID), {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fields: {
          text: { stringValue: body.text },
          timestamp: { timestampValue: timestamp },
        },
      }),
    })

    if (!response.ok) {
      const detail = await response.text()
      return c.json(
        { error: 'Failed to save note', detail },
        response.status as 400 | 401 | 403 | 404 | 500
      )
    }

    const note: Note = { id: SHARED_NOTE_ID, text: body.text, timestamp }
    return c.json(note)
  } catch {
    return c.json({ error: 'Internal Server Error' }, 500)
  }
})

export default app
