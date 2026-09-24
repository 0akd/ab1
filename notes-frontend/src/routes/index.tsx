import { component$, useSignal, $, useTask$ } from "@builder.io/qwik";
import { routeLoader$, type DocumentHead } from "@builder.io/qwik-city";

export interface Note {
  id: string;
  text: string;
  timestamp: string;
}

export const useNotesLoader = routeLoader$(async () => {
  const apiUrl = "https://firestore-api.atrikumar31.workers.dev/api/notes";

  try {
    const response = await fetch(apiUrl);
    if (!response.ok) return "";
    const notes = (await response.json()) as Note[];

    return notes.length > 0 ? notes[0].text : "";
  } catch (error) {
    console.error("Error fetching notes:", error);
    return "";
  }
});

export default component$(() => {
  const initialText = useNotesLoader();
  const textSignal = useSignal(initialText.value);
  const copyStatus = useSignal("Copy");

  const copyToClipboard = $(async () => {
    try {
      await navigator.clipboard.writeText(textSignal.value);
      copyStatus.value = "Copied!";
      setTimeout(() => (copyStatus.value = "Copy"), 2000);
    } catch (err) {
      console.error("Failed to copy:", err);
    }
  });

  useTask$(({ track, cleanup }) => {
    const currentText = track(() => textSignal.value);

    if (currentText === initialText.value) return;

    const timeout = setTimeout(async () => {
      try {
        await fetch("https://firestore-api.atrikumar31.workers.dev/api/notes", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            id: "shared_note",
            text: currentText,
          }),
        });
      } catch (err) {
        console.error("Failed to auto-save:", err);
      }
    }, 1000);

    cleanup(() => clearTimeout(timeout));
  });

  return (
    <div class="flex min-h-screen flex-col items-center bg-gray-50 px-4 py-10">
      <div class="flex w-full max-w-4xl flex-1 flex-col">
        <div class="mb-6 flex items-center justify-between">
          <h1 class="text-3xl font-extrabold text-gray-900">Cloud Clipboard</h1>
          <button
            onClick$={copyToClipboard}
            class="rounded-lg bg-blue-600 px-6 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-blue-500"
          >
            {copyStatus.value}
          </button>
        </div>

        <textarea
          bind:value={textSignal}
          placeholder="Start typing... it will auto-save."
          class="w-full flex-1 resize-none rounded-xl border border-gray-200 bg-white p-6 text-lg whitespace-pre-wrap text-gray-800 shadow-sm transition-shadow focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
        />
      </div>
    </div>
  );
});

export const head: DocumentHead = {
  title: "Cloud Clipboard",
  meta: [
    {
      name: "description",
      content: "A simple cross-platform auto-saving clipboard",
    },
  ],
};
