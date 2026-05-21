import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import ptBR from './locales/pt-BR.json'
import en   from './locales/en.json'

export const resources = {
  'pt-BR': { translation: ptBR },
  en:      { translation: en  },
} as const

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'en',
    supportedLngs: ['pt-BR', 'en'],
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'sagaweaw-lang',
    },
    interpolation: { escapeValue: false },
  })

export default i18n
