// Geriye dönük giriş noktası. Uygulama artık modüler:
//   app/      kök bileşen (durum + iş akışı)
//   views/    Kontrol · İşler · Sohbet · Modeller · Ses
//   layout/   sol ray · üst çubuk · dock · alt gezinme · artefakt paneli
//   settings/ ayarlar bölümleri
//   ui/       paylaşılan bileşenler
//   lib/      saf mantık (akış, gateway, paylaşım, görev sözlüğü…)
//   styles/   tasarım sistemi (design/nova-tokens.json'dan üretilen token'lar)
export { default } from "./app/NovaAgent.jsx";
