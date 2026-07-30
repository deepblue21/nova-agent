import { useEffect, useRef, useState } from "react";

/**
 * prefers-reduced-motion. Hem ref (animasyon döngüleri için, re-render yok)
 * hem de state (koşullu render için) döner.
 */
export function useReducedMotion() {
  const ref = useRef(false);
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const apply = () => {
      ref.current = mq.matches;
      setReduced(mq.matches);
    };
    apply();
    if (mq.addEventListener) mq.addEventListener("change", apply);
    else mq.addListener(apply);
    return () => {
      if (mq.removeEventListener) mq.removeEventListener("change", apply);
      else mq.removeListener(apply);
    };
  }, []);

  return { ref, reduced };
}
