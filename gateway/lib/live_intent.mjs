// Automatic external access requires a concrete live-data/search intent.
export function needsLiveData(text) {
  const s = String(text || '').normalize('NFC').toLocaleLowerCase('tr-TR');
  const explicit = /(?:internette|internetten|web(?:'|’)?de|web arama|search the web|web search|research online|look up)\b/u.test(s);
  if (explicit) return true;
  if (/(?:şiir|hikaye|hikâye|roman|poem|story|çevir|translate|özetle|summarize|düzelt|rewrite)/u.test(s)) return false;
  return /hava\s*durum|hava\s*nas[ıi]l|kaç\s*derece|weather|forecast|son\s*dakika|g[üu]ndem|haberler|\bnews\b|\bheadlines\b/u.test(s)
    || /(?:dolar|euro|altın|altin|d[öo]viz)\s*(?:kur|fiyat|kaç)|(?:borsa|hisse|stock)\s*(?:fiyat|price|değer)|(?:ma[çc]|oyun)\s*(?:skor|sonu[çc])|puan\s*durum/u.test(s)
    || /(?:g[üu]ncel|bug[üu]n|yar[ıi]n|şu an|current|latest|today|tomorrow).{0,40}(?:fiyat|price|s[ıi]cakl[ıi]k|temperature|skor|score|ya[ğg]mur|rüzgar|rüzgâr)/u.test(s);
}
