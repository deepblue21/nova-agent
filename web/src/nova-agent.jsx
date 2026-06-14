import React, { useState, useRef, useEffect } from "react";
import {
  Mic, MessageSquare, Send, Settings, X, Zap, Cpu, Cloud, Sparkles,
  ChevronDown, Square, Brain, Check, Activity, Link2, CircleDot, Waves,
  Copy, RotateCcw, Trash2, Menu, Plus, GitBranch, Eye, Download, Code2, Workflow
} from "lucide-react";
import { extractWebsite } from "./lib/site.mjs";

/* ============================ STYLES ============================ */
const STYLES = `
@import url('https://fonts.googleapis.com/css2?family=Syne:wght@600;700;800&family=Sora:wght@200;300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
* { box-sizing: border-box; margin: 0; padding: 0; }
.vega-root {
  --bg:#06070b; --bg2:#0a0c13; --surface:rgba(255,255,255,0.035); --surface-2:rgba(255,255,255,0.06);
  --line:rgba(255,255,255,0.09); --line-bright:rgba(120,220,255,0.28); --text:#e9edf6; --muted:#8b93a7;
  --muted-2:#5b6276; --cyan:#38e1d6; --azure:#2ba0ff; --coral:#ff8a5b; --glow:0 0 40px rgba(56,225,214,0.25);
  position:relative; width:100%; height:100vh; min-height:640px; background:var(--bg); color:var(--text);
  font-family:'Sora',sans-serif; overflow:hidden; -webkit-font-smoothing:antialiased; display:flex; flex-direction:column;
}
.aurora{position:absolute;inset:0;z-index:0;overflow:hidden;}
.aurora .blob{position:absolute;border-radius:50%;filter:blur(80px);opacity:0.5;mix-blend-mode:screen;animation:drift 22s ease-in-out infinite;}
.aurora .b1{width:520px;height:520px;left:-8%;top:-12%;background:radial-gradient(circle,rgba(43,160,255,0.55),transparent 70%);}
.aurora .b2{width:600px;height:600px;right:-10%;top:8%;background:radial-gradient(circle,rgba(56,225,214,0.45),transparent 70%);animation-delay:-7s;}
.aurora .b3{width:480px;height:480px;left:35%;bottom:-18%;background:radial-gradient(circle,rgba(120,110,255,0.4),transparent 70%);animation-delay:-14s;}
@keyframes drift{0%,100%{transform:translate(0,0) scale(1);}33%{transform:translate(40px,30px) scale(1.08);}66%{transform:translate(-30px,20px) scale(0.95);}}
.grid-overlay{position:absolute;inset:0;z-index:1;pointer-events:none;opacity:0.4;background-image:linear-gradient(rgba(255,255,255,0.025) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,0.025) 1px,transparent 1px);background-size:46px 46px;mask-image:radial-gradient(ellipse 80% 70% at 50% 40%,black 30%,transparent 90%);-webkit-mask-image:radial-gradient(ellipse 80% 70% at 50% 40%,black 30%,transparent 90%);}
.grain{position:absolute;inset:0;z-index:2;pointer-events:none;opacity:0.05;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");}
.topbar{position:relative;z-index:10;display:flex;align-items:center;justify-content:space-between;padding:18px 24px;}
.brand{display:flex;align-items:center;gap:13px;}
.brand-mark{width:38px;height:38px;border-radius:12px;position:relative;background:radial-gradient(circle at 30% 30%,var(--cyan),var(--azure) 60%,#1a1f3a);box-shadow:var(--glow);display:flex;align-items:center;justify-content:center;animation:markPulse 4s ease-in-out infinite;}
@keyframes markPulse{0%,100%{box-shadow:0 0 30px rgba(56,225,214,0.25);}50%{box-shadow:0 0 48px rgba(56,225,214,0.5);}}
.brand-mark::after{content:'';position:absolute;inset:5px;border-radius:8px;background:var(--bg);}
.brand-mark svg{position:relative;z-index:1;}
.brand-text h1{font-family:'Syne',sans-serif;font-weight:800;font-size:19px;letter-spacing:1.5px;line-height:1;background:linear-gradient(90deg,#fff,#a9c7ff);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;}
.brand-text .sub{font-family:'JetBrains Mono',monospace;font-size:10.5px;color:var(--muted);letter-spacing:0.5px;margin-top:3px;}
.brand-text .sub b{color:var(--cyan);font-weight:500;}
.topright{display:flex;align-items:center;gap:10px;}
.status-pill{display:flex;align-items:center;gap:8px;padding:8px 14px;border-radius:100px;background:var(--surface);border:1px solid var(--line);font-size:12px;color:var(--muted);font-family:'JetBrains Mono',monospace;max-width:280px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.dot{width:7px;height:7px;border-radius:50%;background:var(--cyan);box-shadow:0 0 8px var(--cyan);animation:dotPulse 2s infinite;flex-shrink:0;}
.dot.off{background:var(--muted-2);box-shadow:none;animation:none;}
@keyframes dotPulse{0%,100%{opacity:1;}50%{opacity:0.35;}}
.icon-btn{width:40px;height:40px;border-radius:11px;background:var(--surface);border:1px solid var(--line);color:var(--muted);cursor:pointer;display:flex;align-items:center;justify-content:center;transition:all .2s;}
.icon-btn:hover{color:var(--text);border-color:var(--line-bright);background:var(--surface-2);}
.stage{position:relative;z-index:5;flex:1;display:flex;flex-direction:column;min-height:0;}
.voice-view{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:30px;padding:10px 20px;}
.orb-wrap{position:relative;width:340px;height:340px;max-width:70vw;max-height:42vh;}
.orb-canvas{width:100%;height:100%;display:block;}
.voice-status{text-align:center;min-height:54px;}
.voice-status .vs-label{font-family:'Syne',sans-serif;font-weight:700;font-size:23px;letter-spacing:0.5px;background:linear-gradient(90deg,#fff,#8fe9ff);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;}
.voice-status .vs-sub{color:var(--muted);font-size:13px;margin-top:6px;max-width:460px;line-height:1.5;}
.wavebar{display:flex;align-items:center;gap:4px;height:34px;}
.wavebar span{width:3.5px;border-radius:3px;background:linear-gradient(180deg,var(--cyan),var(--azure));transition:height .08s linear;}
.voice-controls{display:flex;align-items:center;gap:16px;}
.mic-btn{width:74px;height:74px;border-radius:50%;cursor:pointer;position:relative;border:1px solid var(--line-bright);background:rgba(56,225,214,0.08);display:flex;align-items:center;justify-content:center;color:var(--cyan);transition:all .25s;}
.mic-btn:hover{background:rgba(56,225,214,0.16);transform:scale(1.04);}
.mic-btn.active{background:linear-gradient(135deg,var(--cyan),var(--azure));color:#04121a;border-color:transparent;box-shadow:0 0 40px rgba(56,225,214,0.5);}
.mic-btn.active::before{content:'';position:absolute;inset:-6px;border-radius:50%;border:1.5px solid rgba(56,225,214,0.5);animation:ripple 1.6s ease-out infinite;}
@keyframes ripple{0%{transform:scale(1);opacity:.8;}100%{transform:scale(1.5);opacity:0;}}
.mic-btn.speaking{background:linear-gradient(135deg,var(--coral),#ff5c7a);color:#190a06;border-color:transparent;box-shadow:0 0 46px rgba(255,138,91,0.5);animation:speakPulse 1.2s ease-in-out infinite;}
.mic-btn.speaking::before{content:'';position:absolute;inset:-7px;border-radius:50%;border:1.5px solid rgba(255,138,91,0.5);animation:ripple 1.4s ease-out infinite;}
@keyframes speakPulse{0%,100%{transform:scale(1);}50%{transform:scale(1.06);}}
.mic-btn:active,.mini-btn:active,.icon-btn:active,.sugg-card:active,.send-btn:active{transform:scale(.95);}
.bubble.ai{transition:border-color .2s,box-shadow .2s;}
.bubble.ai:hover{border-color:var(--line-bright);box-shadow:0 6px 22px rgba(0,0,0,.22);}
/* ---- tasarım yükseltmesi: hero + tema aksanları ---- */
@keyframes hueFlow{0%{background-position:0% 50%;}100%{background-position:200% 50%;}}
@keyframes floatY{0%,100%{transform:translateY(0);}50%{transform:translateY(-7px);}}
@keyframes heroIn{from{opacity:0;transform:translateY(14px) scale(.98);}to{opacity:1;transform:translateY(0) scale(1);}}
.empty-state{animation:heroIn .6s cubic-bezier(.2,.7,.3,1);}
.empty-state .brand-mark{animation:markPulse 4s ease-in-out infinite, floatY 5s ease-in-out infinite;}
.empty-state .es-title{background:linear-gradient(90deg,#fff,#8fe9ff,#a98bff,#fff);background-size:200% auto;-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;animation:hueFlow 7s linear infinite;}
.empty-state .es-title em{-webkit-text-fill-color:var(--cyan);}
.aurora .blob{opacity:0.62;}
.sugg-card{position:relative;overflow:hidden;}
.sugg-card::after{content:'';position:absolute;inset:0;border-radius:15px;padding:1px;background:linear-gradient(135deg,rgba(56,225,214,.55),rgba(120,110,255,.32),transparent 70%);-webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;mask-composite:exclude;opacity:0;transition:opacity .25s;pointer-events:none;}
.sugg-card:hover::after{opacity:1;}
.send-btn{transition:transform .15s,box-shadow .2s,filter .2s;}
.send-btn:hover{box-shadow:0 0 22px rgba(56,225,214,.4);filter:brightness(1.07);}
.status-pill{transition:border-color .2s,background .2s;}
.status-pill:hover{border-color:var(--line-bright);}
/* ---- sohbet görünümü polish ---- */
.chat-scroll{scroll-behavior:smooth;}
.avatar.ai{position:relative;overflow:hidden;}
.avatar.ai::before{content:'';position:absolute;inset:-50%;background:conic-gradient(from 0deg,transparent,rgba(255,255,255,.4),transparent 45%);opacity:0;transition:opacity .3s;}
.avatar.ai svg{position:relative;z-index:1;}
.msg:hover .avatar.ai::before{opacity:1;animation:gemSpin 4s linear infinite;}
.bubble.ai{background:linear-gradient(180deg,rgba(255,255,255,.05),var(--surface));}
.bubble.me{box-shadow:0 4px 18px rgba(43,160,255,.12);}
.code-block{transition:border-color .2s,box-shadow .2s;}
.code-block:hover{border-color:var(--line-bright);box-shadow:0 6px 20px rgba(0,0,0,.3);}
.stat-chip{transition:border-color .15s,color .15s;}
.msg:hover .stat-chip.model{border-color:var(--line-bright);}
.tt-source{transition:transform .15s,border-color .15s,color .15s;}
a.tt-source:hover{transform:translateY(-1px);}
.sched-sel{flex:1;cursor:pointer;color:var(--text);}
.sched-sel option{background:var(--bg2);color:var(--text);}
.mini-btn{height:44px;padding:0 18px;border-radius:100px;cursor:pointer;font-size:13px;background:var(--surface);border:1px solid var(--line);color:var(--muted);display:flex;align-items:center;gap:8px;transition:all .2s;font-family:'Sora',sans-serif;}
.mini-btn:hover{color:var(--text);border-color:var(--line-bright);}
.voice-fallback{display:flex;gap:10px;width:100%;max-width:560px;}
.voice-fallback input{flex:1;height:48px;border-radius:14px;padding:0 18px;color:var(--text);font-size:14px;background:var(--surface);border:1px solid var(--line);outline:none;font-family:'Sora',sans-serif;}
.voice-fallback input:focus{border-color:var(--line-bright);}
.chat-view{flex:1;display:flex;flex-direction:column;min-height:0;width:100%;max-width:820px;margin:0 auto;}
.chat-scroll{flex:1;overflow-y:auto;padding:14px 24px 8px;display:flex;flex-direction:column;gap:18px;}
.chat-scroll::-webkit-scrollbar{width:8px;}
.chat-scroll::-webkit-scrollbar-thumb{background:rgba(255,255,255,0.08);border-radius:8px;}
.empty-state{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:22px;text-align:center;padding:20px;}
.empty-state .es-title{font-family:'Syne',sans-serif;font-weight:700;font-size:27px;}
.empty-state .es-title em{font-style:normal;color:var(--cyan);}
.empty-state .es-sub{color:var(--muted);font-size:14px;max-width:430px;line-height:1.6;}
.suggestions{display:flex;flex-wrap:wrap;gap:10px;justify-content:center;max-width:560px;}
.sugg{padding:11px 16px;border-radius:13px;background:var(--surface);border:1px solid var(--line);font-size:13px;color:var(--muted);cursor:pointer;transition:all .2s;text-align:left;}
.sugg-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:11px;max-width:620px;width:100%;}
.sugg-card{display:flex;align-items:flex-start;gap:12px;padding:14px 15px;border-radius:15px;background:var(--surface);border:1px solid var(--line);cursor:pointer;text-align:left;transition:all .18s;}
.sugg-card:hover{border-color:var(--line-bright);background:var(--surface-2);transform:translateY(-2px);box-shadow:0 8px 24px rgba(0,0,0,0.25);}
.sugg-card .sc-ic{width:34px;height:34px;border-radius:10px;background:linear-gradient(135deg,rgba(56,225,214,0.15),rgba(43,160,255,0.12));display:flex;align-items:center;justify-content:center;color:var(--cyan);flex-shrink:0;}
.sugg-card .sc-cat{font-size:10.5px;letter-spacing:.6px;text-transform:uppercase;color:var(--cyan);font-weight:600;margin-bottom:3px;}
.sugg-card .sc-t{font-size:13.5px;color:var(--text);line-height:1.35;}
.sugg-card .sc-d{font-size:11.5px;color:var(--muted-2);margin-top:3px;}
@media (max-width:620px){.sugg-grid{grid-template-columns:1fr;}}
.sugg:hover{color:var(--text);border-color:var(--line-bright);background:var(--surface-2);transform:translateY(-2px);}
.msg{display:flex;gap:13px;max-width:100%;animation:msgIn .4s cubic-bezier(.2,.7,.3,1);}
@keyframes msgIn{from{opacity:0;transform:translateY(10px);}to{opacity:1;transform:translateY(0);}}
.msg.user{flex-direction:row-reverse;}
.avatar{width:34px;height:34px;border-radius:11px;flex-shrink:0;display:flex;align-items:center;justify-content:center;}
.avatar.ai{background:radial-gradient(circle at 30% 30%,var(--cyan),var(--azure));color:#04121a;box-shadow:var(--glow);}
.avatar.me{background:var(--surface-2);border:1px solid var(--line);color:var(--muted);font-size:13px;font-family:'JetBrains Mono',monospace;}
.bubble{padding:13px 17px;border-radius:16px;font-size:14.5px;line-height:1.65;max-width:78%;white-space:pre-wrap;word-wrap:break-word;}
.bubble.ai{background:var(--surface);border:1px solid var(--line);border-top-left-radius:5px;color:var(--text);}
.bubble.me{background:linear-gradient(135deg,rgba(56,225,214,0.16),rgba(43,160,255,0.16));border:1px solid rgba(56,225,214,0.22);border-top-right-radius:5px;}
.think-trace{background:rgba(255,255,255,0.025);border:1px solid var(--line);border-radius:13px;padding:11px 14px;margin-bottom:10px;max-width:78%;}
.think-head{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--cyan);font-family:'JetBrains Mono',monospace;letter-spacing:0.4px;}
.think-step{display:flex;align-items:center;gap:9px;font-size:12.5px;color:var(--muted);margin-top:8px;opacity:0;animation:stepIn .4s forwards;}
@keyframes stepIn{to{opacity:1;}}
.think-step .sc{width:15px;height:15px;border-radius:50%;border:1.5px solid var(--muted-2);display:flex;align-items:center;justify-content:center;flex-shrink:0;}
.think-step.done .sc{border-color:var(--cyan);background:rgba(56,225,214,0.15);color:var(--cyan);}
.think-real{margin-top:8px;font-family:'JetBrains Mono',monospace;font-size:11.5px;line-height:1.55;color:var(--muted);white-space:pre-wrap;max-height:220px;overflow-y:auto;padding-right:4px;}
.think-real::-webkit-scrollbar{width:6px;}
.think-real::-webkit-scrollbar-thumb{background:rgba(255,255,255,0.08);border-radius:6px;}
.typing{display:flex;gap:5px;padding:6px 2px;}
.typing span{width:7px;height:7px;border-radius:50%;background:var(--cyan);animation:typeBounce 1.2s infinite;}
.typing span:nth-child(2){animation-delay:.2s;}.typing span:nth-child(3){animation-delay:.4s;}
/* Gemini tarzı bekleme: parıldayan iskelet satırlar + nabız logosu */
.gem-wait{display:flex;flex-direction:column;gap:9px;padding:4px 0 2px;min-width:240px;}
.gem-line{height:11px;border-radius:6px;background:linear-gradient(90deg,rgba(56,225,214,0.05) 0%,rgba(56,225,214,0.22) 30%,rgba(43,160,255,0.22) 50%,rgba(56,225,214,0.05) 80%);background-size:220% 100%;animation:gemShimmer 1.5s ease-in-out infinite;}
.gem-line.l1{width:88%;} .gem-line.l2{width:96%;animation-delay:.18s;} .gem-line.l3{width:62%;animation-delay:.36s;}
@keyframes gemShimmer{0%{background-position:120% 0;opacity:.55;}50%{opacity:1;}100%{background-position:-120% 0;opacity:.55;}}
.gem-status{display:flex;align-items:center;gap:9px;font-size:12.5px;color:var(--muted);margin-bottom:2px;}
.gem-orb{width:16px;height:16px;border-radius:50%;background:conic-gradient(from 0deg,var(--cyan),var(--azure),var(--coral),var(--cyan));animation:gemSpin 1.1s linear infinite;box-shadow:0 0 12px rgba(56,225,214,0.5);}
@keyframes gemSpin{to{transform:rotate(360deg);}}
.reason-toggle.agent.on{background:linear-gradient(135deg,rgba(255,138,91,0.18),rgba(43,160,255,0.14));border-color:rgba(255,138,91,0.4);color:var(--coral);}
/* ajan araç kullanım kartı */
.tool-trace{background:rgba(255,138,91,0.05);border:1px solid rgba(255,138,91,0.22);border-radius:13px;padding:10px 13px;max-width:78%;}
.tt-head{display:flex;align-items:center;gap:7px;font-size:12px;color:var(--coral);font-family:'JetBrains Mono',monospace;letter-spacing:.4px;margin-bottom:7px;}
.tt-step{display:grid;grid-template-columns:auto auto 1fr;align-items:center;gap:8px;font-size:12.5px;color:var(--muted);margin-top:5px;}
.tt-ic{width:18px;height:18px;border-radius:6px;background:rgba(255,138,91,0.12);display:flex;align-items:center;justify-content:center;color:var(--coral);flex-shrink:0;}
.tt-name{color:var(--text);font-weight:500;}
.tt-q{color:var(--muted-2);font-family:'JetBrains Mono',monospace;font-size:11.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.tt-sources{grid-column:2 / -1;display:flex;gap:6px;flex-wrap:wrap;margin-top:-1px;}
.tt-source{display:inline-flex;align-items:center;gap:4px;max-width:220px;border:1px solid var(--line);border-radius:999px;padding:3px 7px;background:rgba(255,255,255,.035);color:var(--muted);font-size:10.5px;text-decoration:none;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
a.tt-source:hover{color:var(--cyan);border-color:var(--line-bright);}
/* bilgi tabanı (ayarlar) */
.kb-hint{font-size:12px;color:var(--muted);margin-bottom:9px;line-height:1.5;}
.kb-upload{display:flex;flex-direction:column;gap:7px;}
.kb-title,.kb-text{width:100%;background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:9px 12px;color:var(--text);font-size:13px;font-family:'Sora',sans-serif;outline:none;}
.kb-text{resize:vertical;min-height:60px;line-height:1.5;}
.kb-title:focus,.kb-text:focus{border-color:var(--line-bright);}
.kb-actions{display:flex;gap:8px;justify-content:flex-end;}
.kb-file{display:inline-flex;align-items:center;gap:6px;background:transparent;border:1px solid var(--line);border-radius:9px;padding:8px 12px;color:var(--muted);font-size:12.5px;font-family:'Sora',sans-serif;cursor:pointer;}
.kb-file:hover{color:var(--text);border-color:var(--line-bright);}
.kb-add{background:linear-gradient(135deg,#38e1d6,#2bb3ff);color:#04222b;border:none;border-radius:9px;padding:8px 16px;font-weight:700;font-size:12.5px;cursor:pointer;}
.kb-add:disabled{opacity:.45;cursor:not-allowed;}
.kb-list{display:flex;flex-direction:column;gap:5px;margin-top:11px;}
.kb-row{display:flex;align-items:center;gap:9px;padding:8px 11px;background:var(--surface);border:1px solid var(--line);border-radius:9px;font-size:12.5px;}
.kb-row svg:first-child{color:var(--cyan);flex-shrink:0;}
.kb-name{color:var(--text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.kb-meta{margin-left:auto;color:var(--muted-2);font-size:11px;font-family:'JetBrains Mono',monospace;}
.kb-del{background:none;border:none;color:var(--muted-2);cursor:pointer;display:flex;}
.kb-del:hover{color:var(--coral);}
/* artifacts (canvas) önizleme paneli */
.artifact-panel{position:fixed;top:0;right:0;bottom:0;z-index:60;width:46%;min-width:380px;max-width:720px;background:#0b0e16;border-left:1px solid var(--line);display:flex;flex-direction:column;animation:slideInRight .22s cubic-bezier(.2,.7,.3,1);box-shadow:-18px 0 50px rgba(0,0,0,0.4);}
@keyframes slideInRight{from{transform:translateX(100%);}to{transform:translateX(0);}}
.ap-head{display:flex;align-items:center;justify-content:space-between;padding:13px 16px;border-bottom:1px solid var(--line);}
.ap-title{display:flex;align-items:center;gap:8px;font-size:13.5px;color:var(--text);font-weight:500;}
.ap-title svg{color:var(--cyan);}
.ap-type{font-family:'JetBrains Mono',monospace;font-size:11.5px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px;}
.ap-warn{font-family:'JetBrains Mono',monospace;font-size:10.5px;color:#ffb86b;border:1px solid rgba(255,184,107,.28);border-radius:7px;padding:2px 6px;background:rgba(255,184,107,.08);}
.ap-actions{display:flex;gap:4px;}
.ap-btn{width:32px;height:32px;border-radius:9px;background:transparent;border:1px solid transparent;color:var(--muted);display:flex;align-items:center;justify-content:center;cursor:pointer;transition:all .15s;}
.ap-btn:hover{color:var(--text);border-color:var(--line);background:var(--surface);}
.ap-frame{flex:1;border:none;width:100%;background:#fff;}
.ap-browser{display:flex;align-items:center;gap:6px;padding:8px 12px;background:#0e1320;border-bottom:1px solid var(--line);}
.ap-dot{width:10px;height:10px;border-radius:50%;flex-shrink:0;}
.ap-dot.r{background:#ff5f57;}.ap-dot.y{background:#febc2e;}.ap-dot.g{background:#28c840;}
.ap-url{flex:1;margin-left:8px;display:flex;align-items:center;gap:6px;height:27px;padding:0 12px;border-radius:8px;background:rgba(255,255,255,.05);border:1px solid var(--line);color:var(--muted);font-family:'JetBrains Mono',monospace;font-size:11px;}
.ap-url svg{color:var(--cyan);}
@media (max-width:980px){.artifact-panel{width:100%;min-width:0;max-width:none;}}
.avatar.ai.thinking{animation:gemPulse 1.3s ease-in-out infinite;}
@keyframes gemPulse{0%,100%{box-shadow:0 0 0 0 rgba(56,225,214,0.4);}50%{box-shadow:0 0 0 7px rgba(56,225,214,0);}}
@keyframes typeBounce{0%,60%,100%{transform:translateY(0);opacity:.4;}30%{transform:translateY(-6px);opacity:1;}}
.composer{padding:14px 24px 18px;}
.composer-inner{display:flex;align-items:flex-end;gap:10px;background:var(--surface);border:1px solid var(--line);border-radius:18px;padding:8px 8px 8px 18px;transition:border-color .2s;}
.composer-inner:focus-within{border-color:var(--line-bright);box-shadow:0 0 30px rgba(56,225,214,0.08);}
.composer textarea{flex:1;background:transparent;border:none;outline:none;color:var(--text);font-size:14.5px;font-family:'Sora',sans-serif;resize:none;max-height:120px;line-height:1.5;padding:8px 0;}
.composer textarea::placeholder{color:var(--muted-2);}
.send-btn{width:42px;height:42px;border-radius:13px;border:none;cursor:pointer;flex-shrink:0;background:linear-gradient(135deg,var(--cyan),var(--azure));color:#04121a;display:flex;align-items:center;justify-content:center;transition:all .2s;}
.send-btn:hover{transform:scale(1.05);box-shadow:var(--glow);}
.send-btn:disabled{opacity:0.4;cursor:not-allowed;transform:none;box-shadow:none;}
.send-btn.stop{background:var(--surface-2);color:var(--coral);}
.attach-btn{width:38px;height:38px;border-radius:11px;border:none;cursor:pointer;flex-shrink:0;background:var(--surface-2);color:var(--muted);display:flex;align-items:center;justify-content:center;transition:all .2s;align-self:flex-end;}
.attach-btn:hover{color:var(--cyan);}
.attach-strip{display:flex;gap:8px;padding:0 4px 10px;flex-wrap:wrap;}
.attach-thumb{position:relative;width:56px;height:56px;border-radius:10px;overflow:hidden;border:1px solid var(--line);}
.attach-thumb img{width:100%;height:100%;object-fit:cover;}
.attach-thumb button{position:absolute;top:2px;right:2px;width:18px;height:18px;border-radius:50%;border:none;cursor:pointer;background:rgba(0,0,0,0.6);color:#fff;display:flex;align-items:center;justify-content:center;}
.msg-imgs{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:8px;}
.msg-img{max-width:160px;max-height:160px;border-radius:10px;border:1px solid var(--line);}
.dock{position:relative;z-index:10;display:flex;align-items:center;justify-content:center;gap:12px;padding:0 20px 18px;flex-wrap:wrap;}
.dock-group{display:flex;align-items:center;gap:6px;background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:5px;backdrop-filter:blur(10px);}
.mode-tab{padding:9px 18px;border-radius:10px;cursor:pointer;font-size:13px;color:var(--muted);display:flex;align-items:center;gap:8px;transition:all .2s;border:none;background:transparent;font-family:'Sora',sans-serif;}
.mode-tab.on{background:linear-gradient(135deg,rgba(56,225,214,0.18),rgba(43,160,255,0.14));color:var(--text);box-shadow:inset 0 0 0 1px rgba(56,225,214,0.25);}
.selector{position:relative;}
.sel-btn{display:flex;align-items:center;gap:9px;padding:9px 13px;border-radius:11px;background:transparent;border:none;cursor:pointer;color:var(--text);font-size:13px;font-family:'Sora',sans-serif;transition:background .2s;}
.sel-btn:hover{background:var(--surface-2);}
.sel-btn .sel-icon{color:var(--cyan);display:flex;}
.sel-btn .sel-meta{display:flex;flex-direction:column;align-items:flex-start;line-height:1.1;}
.sel-btn .sel-meta .lbl{font-size:9.5px;color:var(--muted-2);font-family:'JetBrains Mono',monospace;letter-spacing:0.5px;}
.sel-btn .sel-meta .val{font-size:13px;font-weight:500;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.sel-btn .chev{color:var(--muted);transition:transform .2s;}
.sel-btn.open .chev{transform:rotate(180deg);}
.dropdown{position:absolute;bottom:calc(100% + 10px);left:0;min-width:270px;background:#0c0f17;border:1px solid var(--line);border-radius:16px;padding:7px;box-shadow:0 20px 60px rgba(0,0,0,0.6);animation:ddIn .2s ease;z-index:50;max-height:60vh;overflow-y:auto;}
@keyframes ddIn{from{opacity:0;transform:translateY(8px);}to{opacity:1;transform:translateY(0);}}
.dd-group-label{font-size:9.5px;color:var(--muted-2);font-family:'JetBrains Mono',monospace;letter-spacing:0.8px;padding:9px 11px 5px;text-transform:uppercase;}
.dd-item{display:flex;align-items:center;gap:11px;padding:10px 11px;border-radius:10px;cursor:pointer;transition:background .15s;}
.dd-item:hover{background:var(--surface-2);}
.dd-item.sel{background:rgba(56,225,214,0.1);}
.dd-item .di-ic{width:30px;height:30px;border-radius:9px;background:var(--surface-2);display:flex;align-items:center;justify-content:center;color:var(--muted);flex-shrink:0;}
.dd-item.sel .di-ic{color:var(--cyan);background:rgba(56,225,214,0.15);}
.dd-item .di-txt{flex:1;}
.dd-item .di-txt .t{font-size:13.5px;}
.dd-item .di-txt .d{font-size:11px;color:var(--muted);margin-top:2px;font-family:'JetBrains Mono',monospace;}
.dd-item .di-check{color:var(--cyan);}
.dd-custom{display:flex;gap:6px;padding:6px 8px;}
.dd-custom input{flex:1;height:36px;border-radius:9px;padding:0 11px;background:var(--surface);border:1px solid var(--line);color:var(--text);font-size:12.5px;font-family:'JetBrains Mono',monospace;outline:none;}
.dd-custom input:focus{border-color:var(--line-bright);}
.dd-custom button{height:36px;padding:0 12px;border-radius:9px;border:none;cursor:pointer;background:rgba(56,225,214,0.15);color:var(--cyan);font-size:12px;font-family:'Sora',sans-serif;}
.effort{display:flex;gap:3px;background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:5px;}
.eff-opt{padding:8px 12px;border-radius:9px;cursor:pointer;font-size:12px;color:var(--muted);display:flex;align-items:center;gap:6px;transition:all .2s;border:none;background:transparent;font-family:'Sora',sans-serif;white-space:nowrap;}
.eff-opt:hover{color:var(--text);}
.eff-opt.on{color:#04121a;background:linear-gradient(135deg,var(--cyan),var(--azure));}
.reason-toggle{display:flex;align-items:center;gap:8px;padding:8px 12px;border-radius:9px;cursor:pointer;font-size:12px;color:var(--muted);border:none;background:transparent;font-family:'Sora',sans-serif;transition:color .2s;}
.reason-toggle.on{color:var(--cyan);}
.rt-switch{width:30px;height:17px;border-radius:100px;background:var(--surface-2);position:relative;transition:background .2s;flex-shrink:0;}
.reason-toggle.on .rt-switch{background:rgba(56,225,214,0.35);}
.rt-switch::after{content:'';position:absolute;width:13px;height:13px;border-radius:50%;background:var(--muted);top:2px;left:2px;transition:all .2s;}
.reason-toggle.on .rt-switch::after{left:15px;background:var(--cyan);}
.overlay{position:fixed;inset:0;z-index:100;background:rgba(3,4,8,0.7);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;padding:20px;animation:ddIn .2s;}
.modal{width:100%;max-width:520px;background:#0a0d15;border:1px solid var(--line);border-radius:22px;padding:26px;max-height:90vh;overflow-y:auto;}
.modal h2{font-family:'Syne',sans-serif;font-weight:700;font-size:20px;display:flex;align-items:center;gap:10px;}
.modal .m-sub{color:var(--muted);font-size:13px;margin-top:5px;}
.m-section{margin-top:22px;}
.m-section .ms-label{font-size:11px;color:var(--muted-2);font-family:'JetBrains Mono',monospace;letter-spacing:0.6px;text-transform:uppercase;margin-bottom:10px;}
.callout{background:rgba(56,225,214,0.07);border:1px solid rgba(56,225,214,0.2);border-radius:13px;padding:13px 15px;font-size:12.5px;color:#bfeef0;line-height:1.55;}
.callout b{color:var(--cyan);}
.prov-card{background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:13px;margin-bottom:10px;}
.pc-head{display:flex;align-items:center;gap:11px;}
.pc-head .pc-ic{width:34px;height:34px;border-radius:10px;background:var(--surface-2);display:flex;align-items:center;justify-content:center;color:var(--cyan);flex-shrink:0;}
.pc-head .pc-txt{flex:1;}
.pc-head .pc-txt .t{font-size:14px;font-weight:500;}
.pc-head .pc-txt .h{font-size:11.5px;color:var(--muted);margin-top:2px;line-height:1.4;}
.pc-inputs{margin-top:11px;display:flex;flex-direction:column;gap:8px;}
.pc-inputs label{font-size:10px;color:var(--muted-2);font-family:'JetBrains Mono',monospace;letter-spacing:0.5px;}
.pc-inputs input{width:100%;height:40px;border-radius:10px;padding:0 13px;background:var(--bg2);border:1px solid var(--line);color:var(--text);font-size:12.5px;font-family:'JetBrains Mono',monospace;outline:none;}
.pc-inputs input:focus{border-color:var(--line-bright);}
.agent-card{display:flex;align-items:center;gap:13px;padding:13px;border-radius:14px;background:var(--surface);border:1px solid var(--line);cursor:pointer;transition:all .2s;margin-bottom:9px;}
.agent-card:hover{border-color:var(--line-bright);}
.agent-card.sel{border-color:var(--cyan);background:rgba(56,225,214,0.06);}
.agent-card .ac-ic{width:38px;height:38px;border-radius:11px;background:var(--surface-2);display:flex;align-items:center;justify-content:center;color:var(--cyan);flex-shrink:0;}
.agent-card .ac-txt{flex:1;}
.agent-card .ac-txt .t{font-size:14px;font-weight:500;}
.agent-card .ac-txt .d{font-size:12px;color:var(--muted);margin-top:2px;}
.persona-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;}
.persona-card{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:10px;text-align:left;background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:11px;color:var(--text);font-family:'Sora',sans-serif;cursor:pointer;transition:all .18s;min-height:74px;}
.persona-card:hover{border-color:var(--line-bright);background:var(--surface-2);}
.persona-card.sel{border-color:var(--cyan);background:rgba(56,225,214,0.06);}
.persona-card .pp-ic{width:34px;height:34px;border-radius:10px;background:var(--surface-2);display:flex;align-items:center;justify-content:center;color:var(--cyan);flex-shrink:0;}
.persona-card .pp-t{display:block;font-size:13.5px;font-weight:600;line-height:1.25;}
.persona-card .pp-d{display:block;font-size:11.5px;color:var(--muted);line-height:1.35;margin-top:2px;}
.persona-card .pp-check{color:var(--cyan);opacity:.95;}
.persona-custom{margin-top:10px;}
.pc-inputs textarea{width:100%;min-height:92px;border-radius:10px;padding:10px 13px;background:var(--bg2);border:1px solid var(--line);color:var(--text);font-size:12.5px;font-family:'Sora',sans-serif;outline:none;resize:vertical;line-height:1.5;}
.pc-inputs textarea:focus{border-color:var(--line-bright);}
@media (max-width:560px){.persona-grid{grid-template-columns:1fr;}}
@media (max-width:720px){.brand-text .sub{display:none;}.dock{gap:8px;}.eff-opt span.txt{display:none;}.orb-wrap{width:260px;height:260px;}.status-pill{display:none;}}
@media (prefers-reduced-motion: reduce){
  .aurora .blob,.brand-mark,.mic-btn.active::before,.typing span,.dot{animation:none!important;}
}
/* markdown */
.md{font-size:14.5px;line-height:1.65;}
.md p{margin:0 0 9px;}
.md p:last-child{margin-bottom:0;}
.md h1,.md h2,.md h3,.md h4{font-family:'Syne',sans-serif;margin:12px 0 7px;line-height:1.25;}
.md h1{font-size:19px;}.md h2{font-size:17px;}.md h3{font-size:15.5px;}.md h4{font-size:14px;}
.md ul,.md ol{margin:0 0 9px;padding-left:20px;}
.md li{margin:3px 0;}
.md a{color:var(--cyan);text-decoration:underline;text-underline-offset:2px;}
.md blockquote{border-left:2px solid var(--line-bright);margin:0 0 9px;padding:2px 0 2px 12px;color:var(--muted);}
.md-ic{background:rgba(255,255,255,0.08);border:1px solid var(--line);border-radius:5px;padding:1px 5px;font-family:'JetBrains Mono',monospace;font-size:12.5px;}
.code-block{margin:9px 0;border:1px solid var(--line);border-radius:12px;overflow:hidden;background:#070a11;}
.code-bar{display:flex;align-items:center;justify-content:space-between;padding:7px 12px;background:rgba(255,255,255,0.03);border-bottom:1px solid var(--line);}
.code-bar .lang{font-family:'JetBrains Mono',monospace;font-size:10.5px;color:var(--muted-2);letter-spacing:0.5px;text-transform:uppercase;}
.code-copy{display:flex;align-items:center;gap:6px;background:transparent;border:none;cursor:pointer;color:var(--muted);font-size:11.5px;font-family:'Sora',sans-serif;transition:color .15s;}
.code-copy:hover{color:var(--cyan);}
.code-block pre{margin:0;padding:12px 14px;overflow-x:auto;}
.code-block code{font-family:'JetBrains Mono',monospace;font-size:12.5px;line-height:1.6;color:#cfe8ff;white-space:pre;}
.code-block pre::-webkit-scrollbar{height:7px;}
.code-block pre::-webkit-scrollbar-thumb{background:rgba(255,255,255,0.1);border-radius:7px;}
/* message actions + route chip */
.msg-actions{display:flex;gap:5px;margin:6px 0 0 47px;}
.msg-act{display:flex;align-items:center;gap:5px;background:transparent;border:1px solid transparent;border-radius:8px;padding:5px 9px;cursor:pointer;color:var(--muted-2);font-size:11.5px;font-family:'Sora',sans-serif;transition:all .15s;}
.msg-act:hover{color:var(--text);border-color:var(--line);background:var(--surface);}
.route-chip{display:inline-flex;align-items:center;gap:6px;margin:6px 0 0 47px;font-size:10.5px;color:var(--muted);font-family:'JetBrains Mono',monospace;background:rgba(56,225,214,0.07);border:1px solid rgba(56,225,214,0.18);border-radius:7px;padding:3px 8px;}
.route-chip svg{color:var(--cyan);}
.stat-row{display:flex;flex-wrap:wrap;gap:6px;align-items:center;margin:7px 0 0 47px;}
.stat-chip{display:inline-flex;align-items:center;gap:5px;font-size:10.5px;color:var(--muted);font-family:'JetBrains Mono',monospace;background:rgba(255,255,255,0.03);border:1px solid var(--line);border-radius:7px;padding:3px 8px;}
.stat-chip svg{color:var(--muted-2);}
.stat-chip.model{color:#cfe8ff;background:rgba(56,225,214,0.08);border-color:rgba(56,225,214,0.2);}
.stat-chip.model svg{color:var(--cyan);}
.stat-chip.time{margin-left:auto;color:var(--muted-2);}
/* header hızlı model geçişi */
.hdr-model{position:relative;}
.status-pill.clickable{cursor:pointer;border:1px solid var(--line);transition:border-color .15s,background .15s;}
.status-pill.clickable:hover,.status-pill.clickable.open{border-color:var(--line-bright);background:var(--surface-2);}
.hdr-dd{position:absolute;top:calc(100% + 8px);right:0;z-index:80;width:320px;max-height:64vh;overflow-y:auto;background:#0b0e16;border:1px solid var(--line);border-radius:14px;padding:8px;box-shadow:0 18px 50px rgba(0,0,0,0.5);animation:ddIn .16s;}
.hdr-dd .dd-group-label{font-size:10px;letter-spacing:.6px;text-transform:uppercase;color:var(--muted-2);padding:9px 10px 4px;}
.hdr-dd .dd-item{display:flex;align-items:center;gap:11px;padding:9px 10px;border-radius:10px;cursor:pointer;transition:background .12s;}
.hdr-dd .dd-item:hover{background:var(--surface);}
.hdr-dd .dd-item.sel{background:rgba(56,225,214,0.08);}
.hdr-dd .di-ic{width:30px;height:30px;border-radius:8px;background:var(--surface-2);display:flex;align-items:center;justify-content:center;color:var(--cyan);flex-shrink:0;}
.hdr-dd .di-txt .t{font-size:13.5px;font-weight:500;}
.hdr-dd .di-txt .d{font-size:11.5px;color:var(--muted);margin-top:1px;}
.hdr-dd .di-check{margin-left:auto;color:var(--cyan);flex-shrink:0;}
/* kalıcı sol panel */
.side-rail{display:none;}
.rail-hide{display:inline-flex;}
@media (min-width:980px){
  .side-rail{display:flex;flex-direction:column;gap:10px;position:fixed;left:0;top:0;bottom:0;width:264px;z-index:40;background:#080a11;border-right:1px solid var(--line);padding:16px 12px;}
  .vega-root.has-rail .topbar,.vega-root.has-rail main.stage,.vega-root.has-rail .dock{margin-left:264px;}
  .rail-hide{display:none;}
}
.rail-brand{display:flex;align-items:center;gap:9px;font-family:'Syne',sans-serif;font-weight:700;font-size:16px;letter-spacing:1px;padding:4px 6px 6px;}
.rail-search{position:relative;}
.rail-search input{width:100%;background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:9px 30px 9px 12px;color:var(--text);font-size:13px;font-family:'Sora',sans-serif;outline:none;}
.rail-search input:focus{border-color:var(--line-bright);}
.rail-search button{position:absolute;right:7px;top:50%;transform:translateY(-50%);background:none;border:none;color:var(--muted-2);cursor:pointer;display:flex;}
.rail-list{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:3px;margin:2px -4px;padding:0 4px;}
.rail-list::-webkit-scrollbar{width:6px;}
.rail-list::-webkit-scrollbar-thumb{background:rgba(255,255,255,0.08);border-radius:6px;}
.rail-empty{color:var(--muted-2);font-size:12.5px;text-align:center;padding:18px 0;}
.rail-settings{display:flex;align-items:center;gap:8px;background:transparent;border:1px solid var(--line);border-radius:11px;padding:10px 12px;color:var(--muted);font-size:13px;font-family:'Sora',sans-serif;cursor:pointer;transition:all .15s;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.rail-settings:hover{color:var(--text);border-color:var(--line-bright);background:var(--surface);}
.rail-export{display:flex;align-items:center;gap:6px;font-size:11px;color:var(--muted-2);padding:0 2px 2px;}
.rail-export button{display:inline-flex;align-items:center;gap:4px;background:transparent;border:1px solid var(--line);border-radius:8px;padding:5px 9px;color:var(--muted);font-size:11px;font-family:'Sora',sans-serif;cursor:pointer;}
.rail-export button:hover{color:var(--cyan);border-color:var(--line-bright);}
/* üçüncü parti eklenti rozetlerini (Grammarly vb.) kompozerden uzak tut */
grammarly-extension,grammarly-popups,grammarly-card,div[data-grammarly-part]{display:none!important;}
/* conversations drawer */
.drawer-overlay{position:fixed;inset:0;z-index:90;background:rgba(3,4,8,0.55);backdrop-filter:blur(4px);animation:ddIn .2s;}
.drawer{position:fixed;top:0;left:0;bottom:0;z-index:91;width:290px;max-width:84vw;background:#0a0d15;border-right:1px solid var(--line);padding:18px 14px;display:flex;flex-direction:column;gap:6px;animation:slideIn .22s cubic-bezier(.2,.7,.3,1);}
@keyframes slideIn{from{transform:translateX(-100%);}to{transform:translateX(0);}}
.drawer-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;}
.drawer-head .dh-title{font-family:'Syne',sans-serif;font-weight:700;font-size:15px;}
.new-chat{display:flex;align-items:center;gap:9px;width:100%;padding:11px 13px;border-radius:12px;border:1px solid var(--line-bright);background:rgba(56,225,214,0.08);color:var(--cyan);cursor:pointer;font-size:13.5px;font-family:'Sora',sans-serif;margin-bottom:8px;transition:background .2s;}
.new-chat:hover{background:rgba(56,225,214,0.15);}
.conv-list{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:3px;}
.conv-list::-webkit-scrollbar{width:7px;}
.conv-list::-webkit-scrollbar-thumb{background:rgba(255,255,255,0.08);border-radius:7px;}
.conv-row{display:flex;align-items:center;gap:8px;padding:10px 11px;border-radius:10px;cursor:pointer;color:var(--muted);transition:all .15s;}
.conv-row:hover{background:var(--surface-2);color:var(--text);}
.conv-row.on{background:rgba(56,225,214,0.1);color:var(--text);}
.conv-row .cr-title{flex:1;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.conv-row .cr-del{opacity:0;border:none;background:transparent;color:var(--muted-2);cursor:pointer;display:flex;transition:all .15s;}
.conv-row:hover .cr-del{opacity:1;}
.conv-row .cr-del:hover{color:var(--coral);}

@media (max-width:720px){.brand-text .sub{display:none;}.dock{gap:8px;}.eff-opt span.txt{display:none;}.orb-wrap{width:260px;height:260px;}.status-pill{display:none;}}

/* OIDC girişi + kullanım paneli */
.oidc-row{display:flex;align-items:center;gap:10px;margin-top:11px;}
.oidc-btn{display:inline-flex;align-items:center;gap:7px;background:linear-gradient(135deg,#38e1d6,#2bb3ff);color:#04222b;border:none;border-radius:10px;padding:8px 14px;font-weight:700;font-size:12.5px;cursor:pointer;transition:opacity .15s;}
.oidc-btn:hover{opacity:.88;}
.oidc-btn.ghost{background:transparent;border:1px solid var(--line);color:var(--muted);font-weight:500;}
.oidc-badge{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:#38e1d6;background:rgba(56,225,214,0.08);border:1px solid rgba(56,225,214,0.25);padding:6px 11px;border-radius:999px;}
.usage-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-bottom:10px;}
.usage-stat{background:rgba(255,255,255,0.03);border:1px solid var(--line);border-radius:12px;padding:11px 8px;text-align:center;}
.us-v{font-size:16.5px;font-weight:700;color:#38e1d6;font-family:'JetBrains Mono',monospace;}
.us-l{font-size:10.5px;color:var(--muted);margin-top:3px;letter-spacing:.4px;}
.quota-wrap{margin:7px 0 11px;}
.quota-bar{height:7px;border-radius:999px;background:rgba(255,255,255,0.06);overflow:hidden;}
.quota-bar span{display:block;height:100%;background:linear-gradient(90deg,#38e1d6,#2bb3ff);border-radius:999px;transition:width .4s;}
.quota-txt{font-size:11px;color:var(--muted);margin-top:5px;}
.usage-models{display:flex;flex-direction:column;gap:4px;}
.um-row{display:flex;justify-content:space-between;gap:10px;font-size:11.5px;font-family:'JetBrains Mono',monospace;padding:6px 9px;background:rgba(255,255,255,0.02);border:1px solid var(--line);border-radius:8px;}
.um-m{color:#cfe3f0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.um-t{color:var(--muted);white-space:nowrap;}
.cr-cloud{color:#38e1d6;opacity:.7;flex-shrink:0;}
@media (max-width:720px){.usage-grid{grid-template-columns:repeat(2,1fr);}}
`;

/* ============================ DATA ============================ */
const MODELS = [
  { group: "Otomatik · Gateway", items: [
    { id: "auto", name: "Dinamik Yönlendirme", desc: "gateway karar verir", icon: Sparkles, provider: "gateway", model: "auto" },
  ]},
  { group: "Bulut · API Key", items: [
    { id: "opus", name: "Claude Opus 4.x", desc: "anthropic", icon: Cloud, provider: "anthropic", model: "claude-opus-4-20250514" },
    { id: "sonnet", name: "Claude Sonnet 4.x", desc: "anthropic", icon: Cloud, provider: "anthropic", model: "claude-sonnet-4-20250514" },
    { id: "gem-pro", name: "Gemini 2.5 Pro", desc: "google", icon: Cloud, provider: "gemini", model: "gemini-2.5-pro" },
    { id: "gem-flash", name: "Gemini 2.5 Flash", desc: "google", icon: Cloud, provider: "gemini", model: "gemini-2.5-flash" },
    { id: "gpt", name: "GPT-4o mini", desc: "openai uyumlu", icon: Cloud, provider: "openai", model: "gpt-4o-mini" },
  ]},
  { group: "Yerel · Ollama", items: [
    { id: "qwen14", name: "Qwen3 14B", desc: "qwen3:14b", icon: Cpu, provider: "ollama", model: "qwen3:14b" },
    { id: "qwen9", name: "Qwen3.5 9B", desc: "qwen3.5:9b", icon: Cpu, provider: "ollama", model: "qwen3.5:9b" },
    { id: "gemma4", name: "Gemma 4", desc: "gemma4:latest", icon: Cpu, provider: "ollama", model: "gemma4:latest" },
    { id: "gemma4e4b", name: "Gemma 4 E4B", desc: "gemma4:e4b · verimli", icon: Cpu, provider: "ollama", model: "gemma4:e4b" },
    { id: "gemma4e2b", name: "Gemma 4 E2B", desc: "gemma4:e2b · 8GB VRAM'e uygun", icon: Cpu, provider: "ollama", model: "gemma4:e2b" },
    { id: "qwen35omni", name: "Qwen3.5 Omni", desc: "qwen3.5-omni:latest · görsel", icon: Cpu, provider: "ollama", model: "qwen3.5-omni:latest" },
  ]},
  { group: "Güvenlik · Gateway", items: [
    { id: "titus", name: "Titus Cybersecurity", desc: "yerel · SOC/DFIR · TR", icon: Cpu, provider: "gateway", model: "ollama/titus-cyber:latest" },
  ]},
  { group: "Ajan · Gateway", items: [
    { id: "openclaw", name: "OpenClaw Ajanı", desc: "openclaw/default", icon: Link2, provider: "gateway", model: "openclaw/default" },
  ]},
];
const MODEL_FLAT = MODELS.flatMap(g => g.items);

const EFFORTS = [
  { id: "fast", name: "Hızlı", icon: Zap, sys: "Çok kısa, net ve doğrudan yanıt ver. Gereksiz açıklamadan kaçın." },
  { id: "balanced", name: "Dengeli", icon: Activity, sys: "Dengeli, açık ve yeterli ölçüde yanıt ver." },
  { id: "deep", name: "Derin", icon: Brain, sys: "Konuyu derinlemesine ele al; gerektiğinde adım adım açıkla." },
  { id: "max", name: "Maks", icon: Sparkles, sys: "Kapsamlı, titiz ve detaylı bir analiz sun." },
];

const PERSONAS = [
  {
    id: "nova",
    name: "Genel NOVA",
    short: "NOVA",
    desc: "Günlük, teknik ve pratik yardımcı",
    icon: Sparkles,
    sys: "Genel görevlerde dengeli davran; hedefi netleştir, uygulanabilir adımlar ver ve gereksiz uzatma.",
  },
  {
    id: "code-review",
    name: "Kod İnceleyici",
    short: "Kod",
    desc: "Bug, test, güvenlik ve sade düzeltme",
    icon: Code2,
    sys: "Kod üzerinde çalışırken önce hataları, güvenlik risklerini, davranış regresyonlarını ve test boşluklarını öne çıkar. Düzeltmelerde küçük, doğrulanabilir adımlar izle.",
  },
  {
    id: "soc",
    name: "SOC Analisti",
    short: "SOC",
    desc: "Triage, IOC, etki ve kanıt zinciri",
    icon: Activity,
    sys: "Güvenlik olaylarında SOC analisti gibi davran: IOC, etki, kapsam, hipotez, triage adımları, yanlış pozitif olasılığı ve kanıt zincirini belirt. Komut önerilerinde yıkıcı olmayan seçenekleri öncele.",
  },
  {
    id: "architect",
    name: "Mimari Planlayıcı",
    short: "Mimari",
    desc: "Faz, risk, bağımlılık ve rollout",
    icon: GitBranch,
    sys: "Mimari ve ürün kararlarında trade-off, bağımlılık, risk, kullanıcı etkisi ve rollout planını açık belirt. Büyük değişiklikleri fazlara ayır.",
  },
  {
    id: "local-llm",
    name: "Yerel LLM Koçu",
    short: "Yerel",
    desc: "Ollama, VRAM, model seçimi ve gizlilik",
    icon: Cpu,
    sys: "Yerel LLM ve Ollama akışlarında donanım, VRAM, model boyutu, gecikme, gizlilik ve offline çalışma kısıtlarını dikkate al. Önerileri local-first olacak şekilde ver.",
  },
  {
    id: "custom",
    name: "Özel Persona",
    short: "Özel",
    desc: "Kendi sistem yönergeni kaydet",
    icon: Brain,
    sys: "",
  },
];

const PROV_ORDER = ["gateway", "ollama", "anthropic", "gemini", "openai"];
const PROV_META = {
  gateway:   { label: "Gateway", icon: Link2, hint: "Tüm sağlayıcıları tek noktadan yönlendirir (önerilen). Anahtarlar sunucuda kalır, CORS sorunu olmaz.", keyLabel: "Anahtar (opsiyonel)" },
  ollama:    { label: "Ollama (Yerel)", icon: Cpu, hint: "Yerel modeller. Tarayıcıdan erişim için Ollama'yı OLLAMA_ORIGINS=* ile başlat.", keyLabel: null },
  anthropic: { label: "Anthropic", icon: Cloud, hint: "Boş bırakırsan bu önizleme yerleşik bağlantıyı kullanır.", keyLabel: "x-api-key" },
  gemini:    { label: "Google Gemini", icon: Cloud, hint: "API key ile tarayıcıdan çağrılabilir.", keyLabel: "API key" },
  openai:    { label: "OpenAI / uyumlu", icon: Cloud, hint: "Tarayıcı CORS engelliyse Gateway üzerinden kullan.", keyLabel: "API key" },
};

const AGENTS = [
  { id: "direct", name: "Doğrudan LLM", desc: "Ara katman yok", icon: CircleDot, ph: "" },
  { id: "openclaw", name: "OpenClaw", desc: "Self-hosted ajan · skills", icon: Link2, ph: "http://localhost:8787" },
  { id: "hermes", name: "Hermes Agent", desc: "Çok adımlı otomasyon", icon: Link2, ph: "http://localhost:4040" },
];

const THINK_STEPS = ["Bağlam analiz ediliyor", "Niyet çözümleniyor", "Strateji oluşturuluyor", "Yanıt sentezleniyor"];
let mermaidP;
async function loadMermaid() {
  if (!mermaidP) {
    mermaidP = import("mermaid").then(m => {
      const api = m.default || m;
      api.initialize({ startOnLoad: false, theme: "dark", securityLevel: "strict" });
      return api;
    });
  }
  return mermaidP;
}

/* ============================ KALICILIK ============================ */
// Kalıcı depo. Sıra: claude.ai artefakt köprüsü (window.storage) → IndexedDB
// (kendi origin'inde; büyük sohbet geçmişi + data-URL görseller için kota derdi yok)
// → localStorage → bellek. Önceden kendi origin'inde belleğe düşüyordu; bu yüzden
// her sayfa yenilemede API key ve sohbetler sıfırlanıyordu.
const _mem = {};
const _idb = (() => {
  let dbp;
  const open = () => {
    if (!dbp) dbp = new Promise((res, rej) => {
      const rq = indexedDB.open("nova-store", 1);
      rq.onupgradeneeded = () => rq.result.createObjectStore("kv");
      rq.onsuccess = () => res(rq.result);
      rq.onerror = () => rej(rq.error);
    });
    return dbp;
  };
  return {
    async get(k) {
      const db = await open();
      return new Promise((res, rej) => {
        const rq = db.transaction("kv").objectStore("kv").get(k);
        rq.onsuccess = () => res(rq.result != null ? rq.result : null);
        rq.onerror = () => rej(rq.error);
      });
    },
    async set(k, v) {
      const db = await open();
      return new Promise((res, rej) => {
        const tx = db.transaction("kv", "readwrite");
        tx.objectStore("kv").put(v, k);
        tx.oncomplete = () => res();
        tx.onerror = () => rej(tx.error);
      });
    },
  };
})();
const store = {
  async get(k) {
    try { if (typeof window !== "undefined" && window.storage) { const r = await window.storage.get(k); return r ? r.value : null; } } catch (e) {}
    try { if (typeof indexedDB !== "undefined") { const v = await _idb.get(k); if (v != null) return v; } } catch (e) {}
    try { const v = localStorage.getItem(k); if (v != null) return v; } catch (e) {}
    return k in _mem ? _mem[k] : null;
  },
  async set(k, v) {
    try { if (typeof window !== "undefined" && window.storage) { await window.storage.set(k, v); return; } } catch (e) {}
    try { if (typeof indexedDB !== "undefined") { await _idb.set(k, v); return; } } catch (e) {}
    try { localStorage.setItem(k, v); return; } catch (e) {}
    _mem[k] = v;
  },
};
const newId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
const STATE_KEY = "nova:state:v1";
const SHARE_HASH = "#nova-share=";
const MAX_SHARE_CHARS = 150000;

function b64urlEncodeText(text) {
  const bytes = new TextEncoder().encode(text);
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function b64urlDecodeText(text) {
  const b64 = text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4);
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder().decode(bytes);
}
function sharePayload(conv) {
  const messages = (conv.messages || []).map(m => ({
    role: m.role === "user" ? "user" : "assistant",
    content: m.content || "",
    ...(m.route ? { route: m.route } : {}),
    ...(m.stats && m.stats.model ? { stats: { model: m.stats.model } } : {}),
    ...(m.images && m.images.length ? { omittedImages: m.images.length } : {}),
  }));
  return { v: 1, app: "nova-agent", title: conv.title || "NOVA Sohbet", exportedAt: new Date().toISOString(), messages };
}
function convFromSharePayload(payload) {
  if (!payload || payload.app !== "nova-agent" || !Array.isArray(payload.messages)) return null;
  const messages = payload.messages
    .filter(m => m && (m.role === "user" || m.role === "assistant"))
    .map(m => {
      const omitted = Number(m.omittedImages) || 0;
      const note = omitted ? `\n\n[${omitted} görsel paylaşım linkine eklenmedi.]` : "";
      return {
        role: m.role,
        content: String(m.content || "") + note,
        ...(m.route ? { route: String(m.route) } : {}),
        ...(m.stats && m.stats.model ? { stats: { model: String(m.stats.model) } } : {}),
      };
    });
  if (!messages.length) return null;
  return {
    id: newId(),
    title: ("Paylaşılan · " + String(payload.title || "NOVA Sohbet")).slice(0, 80),
    messages,
    imported: true,
    updatedAt: Date.now(),
  };
}
function readSharePayloadFromHash() {
  if (typeof window === "undefined" || !window.location.hash.startsWith(SHARE_HASH)) return null;
  try { return JSON.parse(b64urlDecodeText(window.location.hash.slice(SHARE_HASH.length))); } catch (e) { return null; }
}
function clearShareHash() {
  try {
    if (window.location.hash.startsWith(SHARE_HASH)) {
      window.history.replaceState(null, "", window.location.pathname + window.location.search);
    }
  } catch (e) {}
}

// ---- OIDC (Keycloak) sabitleri + PKCE yardımcıları ----
const OIDC = { issuer: "http://localhost:8081/realms/nova", clientId: "nova-web" };
const AUTH_KEY = "nova:auth:v1";
const b64url = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
async function pkcePair() {
  const verifier = b64url(crypto.getRandomValues(new Uint8Array(32)));
  const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  return { verifier, challenge };
}
function jwtClaim(tok, k) {
  try { return JSON.parse(atob(tok.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")))[k] || ""; } catch (e) { return ""; }
}
const fmtNum = (n) => Number(n || 0).toLocaleString("tr-TR");
function blobToB64(blob) {
  return new Promise((resolve, reject) => {
    const fr = new FileReader();
    fr.onloadend = () => resolve(String(fr.result).split(",")[1] || "");
    fr.onerror = reject;
    fr.readAsDataURL(blob);
  });
}
function b64ToArrayBuffer(b64) {
  const bin = atob(String(b64 || ""));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes.buffer;
}
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

/* ============================ STREAM ENGINE ============================ */
async function readStream(res, onLine) {
  if (!res.ok || !res.body) {
    let t = ""; try { t = await res.text(); } catch (e) {}
    throw new Error("HTTP " + res.status + " " + t.slice(0, 200));
  }
  const reader = res.body.getReader();
  const dec = new TextDecoder();
  let buf = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true });
    let i;
    while ((i = buf.indexOf("\n")) >= 0) { onLine(buf.slice(0, i)); buf = buf.slice(i + 1); }
  }
  if (buf.trim()) onLine(buf);
}
const sseHandler = (onObj) => (line) => {
  const l = line.trim();
  if (!l.startsWith("data:")) return;
  const d = l.slice(5).trim();
  if (!d || d === "[DONE]") return;
  try { onObj(JSON.parse(d)); } catch (e) {}
};
const ndjsonHandler = (onObj) => (line) => {
  const l = line.trim(); if (!l) return;
  try { onObj(JSON.parse(l)); } catch (e) {}
};

const trim = (s) => (s || "").replace(/\/+$/, "");

// ---- çoklu-medya yardımcıları (m.images: data URL dizisi) ----
function dataUrlParts(u) { const mm = /^data:([^;]+);base64,(.*)$/.exec(u) || []; return { mime: mm[1] || "image/png", b64: mm[2] || "" }; }
function oaContent(m) { // OpenAI-tarzı içerik
  if (!m.images || !m.images.length) return m.content;
  const arr = []; if (m.content) arr.push({ type: "text", text: m.content });
  for (const u of m.images) arr.push({ type: "image_url", image_url: { url: u } });
  return arr;
}
function ollamaMsg(m) { const o = { role: m.role, content: m.content || "" }; if (m.images && m.images.length) o.images = m.images.map(u => dataUrlParts(u).b64); return o; }
function geminiParts(m) { const parts = []; if (m.content) parts.push({ text: m.content }); (m.images || []).forEach(u => { const { mime, b64 } = dataUrlParts(u); parts.push({ inlineData: { mimeType: mime, data: b64 } }); }); return parts.length ? parts : [{ text: "" }]; }
function anthroMsg(m) {
  if (!m.images || !m.images.length) return { role: m.role, content: m.content };
  const blocks = m.images.map(u => { const { mime, b64 } = dataUrlParts(u); return { type: "image", source: { type: "base64", media_type: mime, data: b64 } }; });
  if (m.content) blocks.push({ type: "text", text: m.content });
  return { role: m.role, content: blocks };
}

async function streamChat({ prov, model, system, history, think, onToken, signal, extra, onRoute, onThought, onTool }) {
  const kind = prov.kind;
  const H = { "Content-Type": "application/json" };

  if (kind === "ollama") {
    const res = await fetch(trim(prov.baseUrl) + "/api/chat", {
      method: "POST", headers: H, signal,
      body: JSON.stringify({ model, stream: true, think: !!think, messages: [{ role: "system", content: system }, ...history.map(ollamaMsg)] }),
    });
    await readStream(res, ndjsonHandler(o => {
      const th = o && o.message && o.message.thinking; if (th && onThought) onThought(th);
      const t = o && o.message && o.message.content; if (t) onToken(t);
    }));
    return;
  }
  if (kind === "gemini") {
    const url = trim(prov.baseUrl) + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + encodeURIComponent(prov.apiKey);
    const contents = history.map(m => ({ role: m.role === "assistant" ? "model" : "user", parts: geminiParts(m) }));
    const body = { contents, systemInstruction: { parts: [{ text: system }] } };
    if (think) body.generationConfig = { thinkingConfig: { includeThoughts: true } };
    const res = await fetch(url, { method: "POST", headers: H, signal, body: JSON.stringify(body) });
    await readStream(res, sseHandler(o => {
      const parts = (o.candidates && o.candidates[0] && o.candidates[0].content && o.candidates[0].content.parts) || [];
      for (const p of parts) {
        if (!p.text) continue;
        if (p.thought && onThought) onThought(p.text); else onToken(p.text);
      }
    }));
    return;
  }
  if (kind === "anthropic") {
    if (prov.apiKey) {
      const body = { model, max_tokens: think ? 3072 : 1024, system, messages: history.map(anthroMsg), stream: true };
      if (think) body.thinking = { type: "enabled", budget_tokens: 1536 };
      const res = await fetch(trim(prov.baseUrl) + "/v1/messages", {
        method: "POST", signal,
        headers: { ...H, "x-api-key": prov.apiKey, "anthropic-version": "2023-06-01", "anthropic-dangerous-direct-browser-access": "true" },
        body: JSON.stringify(body),
      });
      await readStream(res, sseHandler(o => {
        if (o.type !== "content_block_delta" || !o.delta) return;
        if (o.delta.type === "thinking_delta" && o.delta.thinking) { if (onThought) onThought(o.delta.thinking); return; }
        if (o.delta.text) onToken(o.delta.text);
      }));
      return;
    }
    // built-in proxy fallback (no key) — works inside this preview
    const res = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST", headers: H, signal,
      body: JSON.stringify({ model: "claude-sonnet-4-20250514", max_tokens: 1024, system, messages: history.map(anthroMsg) }),
    });
    const data = await res.json();
    const text = (data.content || []).filter(b => b.type === "text").map(b => b.text).join("\n");
    onToken(text);
    return;
  }
  // openai-compatible (openai, gateway, openrouter, ollama /v1 …)
  const res = await fetch(trim(prov.baseUrl) + "/chat/completions", {
    method: "POST", signal,
    headers: prov.apiKey ? { ...H, "Authorization": "Bearer " + prov.apiKey } : H,
    body: JSON.stringify({ model, stream: true, messages: [{ role: "system", content: system }, ...history.map(m => ({ role: m.role, content: oaContent(m) }))], ...(extra || {}) }),
  });
  if (onRoute) { try { const r = res.headers.get("x-nova-route"); if (r) onRoute(r); } catch (e) {} }
  await readStream(res, sseHandler(o => {
    const d = o.choices && o.choices[0] && o.choices[0].delta;
    if (d && d.reasoning_content && onThought) onThought(d.reasoning_content); // gateway think relay
    if (d && d.tool_step && onTool) {                                          // ajan: yapısal araç adımı
      const ts = d.tool_step; const q = ts.args && (ts.args.query || ts.args.expression || "");
      onTool({ name: ts.name, q, done: !!ts.done, sources: ts.sources || [] });
    }
    const t = d && d.content; if (t) onToken(t);
  }));
}

function errHint(e, prov) {
  const m = (e && e.message) || String(e);
  if (e && e.name === "AbortError") return "";
  const net = /Failed to fetch|NetworkError|TypeError|load failed/i.test(m);
  if (prov.kind === "ollama")
    return "⚠️ Ollama'ya ulaşılamadı (" + prov.baseUrl + "). Açık mı? Tarayıcı erişimi için `OLLAMA_ORIGINS=* ollama serve`. Detay: " + m;
  if (net)
    return "⚠️ Bağlantı/CORS engeli. Tarayıcıdan doğrudan çağrı kapalı olabilir — Gateway'i çalıştırıp 'Dinamik Yönlendirme' (gateway) sağlayıcısını seç. Detay: " + m;
  return "⚠️ Hata: " + m;
}

/* ============================ ORB ============================ */
function useOrb(canvasRef, voiceStateRef, waveRef, reducedRef, extLevelRef) {
  const levelRef = useRef(0.08);
  const colRef = useRef([56, 225, 214]);   // duruma göre yumuşak geçen orb rengi
  useEffect(() => {
    const cv = canvasRef.current; if (!cv) return;
    const ctx = cv.getContext("2d");
    let raf, start = performance.now();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    function resize() { const r = cv.getBoundingClientRect(); cv.width = r.width * dpr; cv.height = r.height * dpr; }
    resize();
    const ro = new ResizeObserver(resize); ro.observe(cv);
    const palette = [[56,225,214],[43,160,255],[120,110,255],[255,138,91]];
    const STATE_COL = { idle:[56,225,214], listening:[43,160,255], thinking:[150,120,255], speaking:[255,138,91] };
    function targetLevel(t) {
      // gerçek ses (mikrofon/TTS) varsa onu kullan
      if (extLevelRef && extLevelRef.current >= 0) return Math.min(1, extLevelRef.current);
      const s = voiceStateRef.current;
      if (reducedRef && reducedRef.current) return 0.05;
      const n = (Math.sin(t*0.013)*0.5+0.5)*(Math.sin(t*0.027)*0.5+0.5);
      if (s === "listening") return 0.30 + n*0.45;
      if (s === "thinking")  return 0.18 + (Math.sin(t*0.006)*0.5+0.5)*0.20;
      if (s === "speaking")  return 0.34 + n*0.50;
      return 0.06 + (Math.sin(t*0.0018)*0.5+0.5)*0.08;
    }
    function frame(now) {
      const reduced = reducedRef && reducedRef.current;
      const sp = reduced ? 0.12 : 1;          // reduced-motion: yavaşlat
      const t = (now - start) * sp, W = cv.width, H = cv.height;
      levelRef.current += (targetLevel(t) - levelRef.current) * 0.12;
      const level = levelRef.current, cx = W/2, cy = H/2;
      const tcol = STATE_COL[voiceStateRef.current] || STATE_COL.idle;
      const col = colRef.current;
      for (let k=0;k<3;k++) col[k] += (tcol[k]-col[k])*0.04;
      const C0=Math.round(col[0]), C1=Math.round(col[1]), C2=Math.round(col[2]);
      const base = Math.min(W,H)*0.25, R = base*(1 + level*0.22);
      ctx.clearRect(0,0,W,H);
      let g = ctx.createRadialGradient(cx,cy,0,cx,cy,R*2.6);
      g.addColorStop(0, "rgba("+C0+","+C1+","+C2+"," + (0.16+level*0.28) + ")");
      g.addColorStop(0.5, "rgba(43,140,255,0.05)"); g.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = g; ctx.fillRect(0,0,W,H);
      ctx.globalCompositeOperation = "lighter";
      for (let i=0;i<5;i++) {
        const a = t*0.0006*(1+i*0.15) + i*(Math.PI*2/5);
        const rad = R*(0.42 + 0.20*Math.sin(t*0.001*(1+i)+i));
        const dist = R*0.30*(0.6 + 0.4*Math.sin(t*0.0008*(i+1)));
        const x = cx + Math.cos(a)*dist*(1+level*0.5), y = cy + Math.sin(a*1.1)*dist*(1+level*0.5);
        const c = palette[i%palette.length];
        const bg = ctx.createRadialGradient(x,y,0,x,y,rad);
        bg.addColorStop(0, "rgba("+c[0]+","+c[1]+","+c[2]+","+(0.5+level*0.3)+")");
        bg.addColorStop(1, "rgba("+c[0]+","+c[1]+","+c[2]+",0)");
        ctx.fillStyle = bg; ctx.beginPath(); ctx.arc(x,y,rad,0,Math.PI*2); ctx.fill();
      }
      const core = ctx.createRadialGradient(cx,cy,0,cx,cy,R*0.55);
      core.addColorStop(0, "rgba(238,255,255," + (0.5+level*0.4) + ")");
      core.addColorStop(0.4, "rgba(170,240,255,0.22)"); core.addColorStop(1, "rgba(170,240,255,0)");
      ctx.fillStyle = core; ctx.beginPath(); ctx.arc(cx,cy,R*0.55,0,Math.PI*2); ctx.fill();
      ctx.globalCompositeOperation = "source-over";
      for (let i=0;i<54;i++) {
        const a = (i/54)*Math.PI*2 + t*0.0003;
        const w = Math.sin(t*0.0022 + i*0.5)*0.5+0.5;
        const rr = R*1.30 + w*8*(1+level*2);
        const x = cx + Math.cos(a)*rr, y = cy + Math.sin(a)*rr, s = (0.7 + w*1.6*(1+level))*dpr;
        ctx.fillStyle = "rgba(150,230,255," + (0.12 + w*0.5) + ")";
        ctx.beginPath(); ctx.arc(x,y,s,0,Math.PI*2); ctx.fill();
      }
      ctx.strokeStyle = "rgba("+C0+","+C1+","+C2+"," + (0.10+level*0.20) + ")"; ctx.lineWidth = 1*dpr;
      ctx.beginPath(); ctx.arc(cx,cy,R*1.16,0,Math.PI*2); ctx.stroke();
      const arcA = t*0.0012;
      ctx.strokeStyle = "rgba("+C0+","+C1+","+C2+"," + (0.5+level*0.35) + ")"; ctx.lineWidth = 2*dpr;
      ctx.beginPath(); ctx.arc(cx,cy,R*1.16, arcA, arcA+0.9); ctx.stroke();

      // dalga çubuklarını DOM üzerinden güncelle (re-render YOK)
      const wc = waveRef && waveRef.current;
      if (wc) {
        const idle = voiceStateRef.current === "idle";
        const bars = wc.children;
        for (let i=0;i<bars.length;i++) {
          const j = Math.sin(now*0.012 + i*0.6)*0.5+0.5;
          const h = Math.max(4, (5 + level*36) * (0.4 + j*0.9));
          bars[i].style.height = h + "px";
          bars[i].style.opacity = idle ? 0.35 : 0.9;
        }
      }
      raf = requestAnimationFrame(frame);
    }
    raf = requestAnimationFrame(frame);
    return () => { cancelAnimationFrame(raf); ro.disconnect(); };
  }, [canvasRef, voiceStateRef, waveRef, reducedRef, extLevelRef]);
  return levelRef;
}

/* ============================ APP ============================ */
export default function App() {
  const [mode, setMode] = useState("voice");
  const [modelId, setModelId] = useState("auto");
  const [customModel, setCustomModel] = useState("");
  const [effort, setEffort] = useState("balanced");
  const [reasoning, setReasoning] = useState(true);
  const [personaId, setPersonaId] = useState("nova");
  const [customPersona, setCustomPersona] = useState("");
  const [openDD, setOpenDD] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  const [agentMode, setAgentMode] = useState(false); // araç çağırma (web arama, hesap, saat)
  const [artifact, setArtifact] = useState(null);    // {type, code, lang} — yan önizleme paneli
  const [auth, setAuth] = useState(null);            // Keycloak oturumu {access_token, refresh_token, expires_at, email}
  const [usageInfo, setUsageInfo] = useState(null);  // /v1/usage cevabı (ayarlar paneli)
  const [gatewayInfo, setGatewayInfo] = useState(null); // /health: aktif default + vision routing
  const [docs, setDocs] = useState([]);              // bilgi tabanı belgeleri
  const [docText, setDocText] = useState("");
  const [docTitle, setDocTitle] = useState("");
  const [docFile, setDocFile] = useState(null);      // {name,mime,b64} — PDF/DOCX server-side extraction
  const [docError, setDocError] = useState("");
  const [docBusy, setDocBusy] = useState(false);
  const [schedTasks, setSchedTasks] = useState([]);      // zamanlanmış/otomatik ajan görevleri
  const [schedForm, setSchedForm] = useState({ title: "", prompt: "", schedule: "daily:09:00" });
  const [schedBusy, setSchedBusy] = useState(false);
  const docFileRef = useRef(null);

  const [providers, setProviders] = useState({
    gateway:   { kind: "openai",    baseUrl: "http://localhost:8088/v1", apiKey: "" },
    ollama:    { kind: "ollama",    baseUrl: "http://localhost:11434",   apiKey: "" },
    anthropic: { kind: "anthropic", baseUrl: "https://api.anthropic.com", apiKey: "" },
    gemini:    { kind: "gemini",    baseUrl: "https://generativelanguage.googleapis.com", apiKey: "" },
    openai:    { kind: "openai",    baseUrl: "https://api.openai.com/v1", apiKey: "" },
  });
  const setProv = (id, patch) => setProviders(p => ({ ...p, [id]: { ...p[id], ...patch } }));

  const [agent, setAgent] = useState("direct");
  const [agentUrl, setAgentUrl] = useState("");

  // çoklu sohbet
  const [convs, setConvs] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [hydrated, setHydrated] = useState(false);
  const [showChats, setShowChats] = useState(false);
  const [convSearch, setConvSearch] = useState("");
  const [shareNote, setShareNote] = useState("");

  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);

  const [voiceState, setVoiceState] = useState("idle");
  const [voiceSub, setVoiceSub] = useState("Konuşmak için mikrofona dokun");
  const [sttSupported, setSttSupported] = useState(false);
  const [voiceText, setVoiceText] = useState("");

  const voiceStateRef = useRef("idle");
  const canvasRef = useRef(null);
  const waveRef = useRef(null);
  const reducedRef = useRef(false);
  const extLevelRef = useRef(-1);            // >=0 ise gerçek ses seviyesi
  const levelRef = useOrb(canvasRef, voiceStateRef, waveRef, reducedRef, extLevelRef);
  const scrollRef = useRef(null);
  const recogRef = useRef(null);
  const abortRef = useRef(null);
  const audioCtxRef = useRef(null);
  const mediaRecRef = useRef(null);
  const mediaStreamRef = useRef(null);
  const meterRafRef = useRef(null);
  const ttsAudioRef = useRef(null);

  // gerçek ses ayarları
  // Aynı origin üzerinden (Caddy /stt /tts → whisper/tts container'ları). Eski
  // kayıtlı ayarlardaki localhost:8088 uçları da aşağıdaki hydrate'te göç ettirilir.
  const [voiceCfg, setVoiceCfg] = useState({ real: false, queued: false, sttUrl: "/stt", ttsUrl: "/tts", jobUrl: "/v1/voice/jobs", voice: "alloy" });
  const setVc = (patch) => setVoiceCfg(v => ({ ...v, ...patch }));

  // çoklu-medya: gönderilmeyi bekleyen görseller (data URL)
  const [pending, setPending] = useState([]);
  const fileRef = useRef(null);
  function addImages(files) {
    const list = Array.from(files || []).filter(f => f.type.startsWith("image/")).slice(0, 4);
    list.forEach(f => { const fr = new FileReader(); fr.onload = () => setPending(p => [...p, String(fr.result)]); fr.readAsDataURL(f); });
  }

  const active = convs.find(c => c.id === activeId) || null;
  const messages = active ? active.messages : [];
  function setMessages(updater) {
    setConvs(prev => prev.map(c => {
      if (c.id !== activeId) return c;
      const nm = typeof updater === "function" ? updater(c.messages) : updater;
      let title = c.title;
      if (title === "Yeni sohbet" || !title) { const u = nm.find(m => m.role === "user"); if (u) title = u.content.slice(0, 42); }
      return { ...c, messages: nm, title, updatedAt: Date.now() };
    }));
  }

  useEffect(() => { voiceStateRef.current = voiceState; }, [voiceState]);
  useEffect(() => { const SR = window.SpeechRecognition || window.webkitSpeechRecognition; setSttSupported(!!SR); }, []);
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    reducedRef.current = mq.matches;
    const h = () => { reducedRef.current = mq.matches; };
    mq.addEventListener ? mq.addEventListener("change", h) : mq.addListener(h);
    return () => { mq.removeEventListener ? mq.removeEventListener("change", h) : mq.removeListener(h); };
  }, []);
  useEffect(() => { if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight; }, [convs, busy]);

  // --- yükle (mount) ---
  useEffect(() => {
    (async () => {
      const imported = convFromSharePayload(readSharePayloadFromHash());
      if (imported) { clearShareHash(); setMode("chat"); }
      const raw = await store.get(STATE_KEY);
      if (raw) {
        try {
          const s = JSON.parse(raw);
          if (s.settings) {
            const g = s.settings;
            if (g.modelId) setModelId(g.modelId);
            if (g.customModel) setCustomModel(g.customModel);
            if (g.effort) setEffort(g.effort);
            if (typeof g.reasoning === "boolean") setReasoning(g.reasoning);
            if (g.personaId && PERSONAS.some(p => p.id === g.personaId)) setPersonaId(g.personaId);
            if (typeof g.customPersona === "string") setCustomPersona(g.customPersona);
            if (typeof g.agentMode === "boolean") setAgentMode(g.agentMode);
            if (g.agent) setAgent(g.agent);
            if (g.agentUrl) setAgentUrl(g.agentUrl);
            if (g.voiceCfg) setVoiceCfg(v => {
              const vc = { ...v, ...g.voiceCfg };
              if (/localhost:8088\/stt/.test(vc.sttUrl || "")) vc.sttUrl = "/stt";   // eski uçtan göç
              if (/localhost:8088\/tts/.test(vc.ttsUrl || "")) vc.ttsUrl = "/tts";
              if (!vc.jobUrl) vc.jobUrl = "/v1/voice/jobs";
              if (vc.queued == null) vc.queued = false;
              return vc;
            });
            if (g.providers) setProviders(p => {
              const merged = { ...p };
              for (const k of Object.keys(p)) merged[k] = { ...p[k], ...(g.providers[k] || {}) };
              return merged;
            });
          }
          if (Array.isArray(s.convs) && s.convs.length) {
            const nextConvs = imported ? [imported, ...s.convs] : s.convs;
            setConvs(nextConvs);
            setActiveId(imported ? imported.id : (s.activeId && s.convs.some(c => c.id === s.activeId) ? s.activeId : s.convs[0].id));
            setHydrated(true);
            return;
          }
        } catch (e) {}
      }
      const id = imported ? imported.id : newId();
      setConvs([imported || { id, title: "Yeni sohbet", messages: [], updatedAt: Date.now() }]);
      setActiveId(id);
      setHydrated(true);
    })();
  }, []);

  // --- kaydet (debounce) ---
  useEffect(() => {
    if (!hydrated) return;
    const t = setTimeout(() => {
      store.set(STATE_KEY, JSON.stringify({
        v: 1,
        settings: { modelId, customModel, effort, reasoning, personaId, customPersona, agentMode, agent, agentUrl, voiceCfg, providers },
        convs, activeId,
      }));
    }, 400);
    return () => clearTimeout(t);
  }, [hydrated, convs, activeId, modelId, customModel, effort, reasoning, personaId, customPersona, agentMode, agent, agentUrl, voiceCfg, providers]);

  // ============================ OIDC (Keycloak) ============================
  async function tokenRequest(params) {
    const r = await fetch(OIDC.issuer + "/protocol/openid-connect/token", {
      method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: params,
    });
    if (!r.ok) throw new Error("token " + r.status);
    return r.json();
  }
  function applyTokens(t) {
    const a = {
      access_token: t.access_token, refresh_token: t.refresh_token,
      expires_at: Date.now() + Math.max(60, (t.expires_in || 300) - 30) * 1000,
      email: jwtClaim(t.access_token, "email"),
    };
    setAuth(a); store.set(AUTH_KEY, JSON.stringify(a));
    setProv("gateway", { apiKey: t.access_token });   // tüm gateway çağrıları JWT kullanır
  }
  async function loginOidc() {
    const { verifier, challenge } = await pkcePair();
    const state = newId();
    try { sessionStorage.setItem("nova:pkce", JSON.stringify({ verifier, state })); } catch (e) {}
    const u = new URL(OIDC.issuer + "/protocol/openid-connect/auth");
    u.searchParams.set("client_id", OIDC.clientId);
    u.searchParams.set("response_type", "code");
    u.searchParams.set("redirect_uri", location.origin + "/");
    u.searchParams.set("scope", "openid profile email");
    u.searchParams.set("state", state);
    u.searchParams.set("code_challenge", challenge);
    u.searchParams.set("code_challenge_method", "S256");
    location.assign(u.toString());
  }
  function logoutOidc() {
    setAuth(null); store.set(AUTH_KEY, "");
    setProv("gateway", { apiKey: "" });
  }
  // login dönüşü (?code=) + kayıtlı oturumu geri yükle
  useEffect(() => { (async () => {
    const qs = new URLSearchParams(location.search);
    if (qs.get("code")) {
      try {
        const pk = JSON.parse(sessionStorage.getItem("nova:pkce") || "{}");
        if (pk.state && qs.get("state") === pk.state) {
          applyTokens(await tokenRequest(new URLSearchParams({
            grant_type: "authorization_code", client_id: OIDC.clientId,
            code: qs.get("code"), redirect_uri: location.origin + "/", code_verifier: pk.verifier })));
        }
      } catch (e) {}
      try { window.history.replaceState({}, "", location.pathname); } catch (e) {}
      return;
    }
    try {
      const raw = await store.get(AUTH_KEY);
      if (!raw) return;
      const a = JSON.parse(raw);
      if (!a || !a.refresh_token) return;
      if (a.expires_at > Date.now() + 60000) { setAuth(a); setProv("gateway", { apiKey: a.access_token }); }
      else applyTokens(await tokenRequest(new URLSearchParams({
        grant_type: "refresh_token", client_id: OIDC.clientId, refresh_token: a.refresh_token })));
    } catch (e) {}
  })(); }, []);
  // süre dolmadan sessiz tazeleme
  useEffect(() => {
    if (!auth || !auth.refresh_token) return;
    const t = setInterval(async () => {
      if (auth.expires_at - Date.now() > 120000) return;
      try {
        applyTokens(await tokenRequest(new URLSearchParams({
          grant_type: "refresh_token", client_id: OIDC.clientId, refresh_token: auth.refresh_token })));
      } catch (e) { logoutOidc(); }
    }, 30000);
    return () => clearInterval(t);
  }, [auth]);

  // ---- kullanım paneli verisi (ayarlar açılınca) ----
  useEffect(() => {
    if (!showSettings) return;
    const k = providers.gateway.apiKey;
    if (!k) { setUsageInfo(null); return; }
    fetch(trim(providers.gateway.baseUrl).replace(/\/v1$/, "") + "/v1/usage", { headers: { Authorization: "Bearer " + k } })
      .then(r => (r.ok ? r.json() : null)).then(setUsageInfo).catch(() => setUsageInfo(null));
  }, [showSettings, providers.gateway.apiKey]);

  // ---- gateway health: default/vision model bilgisi (public endpoint) ----
  useEffect(() => {
    if (!showSettings) return;
    fetch(trim(providers.gateway.baseUrl).replace(/\/v1$/, "") + "/health")
      .then(r => (r.ok ? r.json() : null)).then(setGatewayInfo).catch(() => setGatewayInfo(null));
  }, [showSettings, providers.gateway.baseUrl]);

  // ---- bilgi tabanı (RAG) ----
  const kbBase = () => trim(providers.gateway.baseUrl).replace(/\/v1$/, "");
  async function loadDocs() {
    const k = providers.gateway.apiKey; if (!k) return;
    try { const r = await fetch(kbBase() + "/v1/knowledge", { headers: { Authorization: "Bearer " + k } }); if (r.ok) setDocs((await r.json()).data || []); } catch (e) {}
  }
  useEffect(() => { if (showSettings) loadDocs(); }, [showSettings, providers.gateway.apiKey]);
  useEffect(() => { if (showSettings) loadSchedTasks(); }, [showSettings, providers.gateway.apiKey]);
  async function uploadDoc() {
    const k = providers.gateway.apiKey; const text = docText.trim();
    if (!k || docBusy || (!docFile && text.length < 20)) return;
    setDocError("");
    setDocBusy(true);
    try {
      const body = docFile
        ? { title: docTitle.trim() || docFile.name.replace(/\.[^.]+$/, ""), file: docFile }
        : { title: docTitle.trim() || "Belge", text };
      const r = await fetch(kbBase() + "/v1/knowledge", {
        method: "POST", headers: { "Content-Type": "application/json", Authorization: "Bearer " + k },
        body: JSON.stringify(body),
      });
      if (r.ok) { setDocText(""); setDocTitle(""); setDocFile(null); await loadDocs(); }
      else {
        let msg = "Belge yüklenemedi";
        try { msg = (await r.json()).error || msg; } catch (e) {}
        setDocError(msg);
      }
    } catch (e) { setDocError((e && e.message) || "Belge yüklenemedi"); }
    finally { setDocBusy(false); }
  }
  async function deleteDoc(id) {
    const k = providers.gateway.apiKey; if (!k) return;
    try { await fetch(kbBase() + "/v1/knowledge/" + id, { method: "DELETE", headers: { Authorization: "Bearer " + k } }); await loadDocs(); } catch (e) {}
  }
  // ---- zamanlanmış / otomatik ajan görevleri ----
  async function loadSchedTasks() {
    const k = providers.gateway.apiKey; if (!k) return;
    try { const r = await fetch(kbBase() + "/v1/scheduled", { headers: { Authorization: "Bearer " + k } }); if (r.ok) setSchedTasks((await r.json()).data || []); } catch (e) {}
  }
  async function createSchedTask() {
    const k = providers.gateway.apiKey; const title = schedForm.title.trim(), prompt = schedForm.prompt.trim();
    if (!k || schedBusy || !title || prompt.length < 3) return;
    setSchedBusy(true);
    try {
      const r = await fetch(kbBase() + "/v1/scheduled", {
        method: "POST", headers: { "Content-Type": "application/json", Authorization: "Bearer " + k },
        body: JSON.stringify({ title, prompt, schedule: schedForm.schedule, agent: true }),
      });
      if (r.ok) { setSchedForm({ title: "", prompt: "", schedule: schedForm.schedule }); await loadSchedTasks(); }
    } catch (e) {} finally { setSchedBusy(false); }
  }
  async function toggleSchedTask(t) {
    const k = providers.gateway.apiKey; if (!k) return;
    try { await fetch(kbBase() + "/v1/scheduled/" + t.id, { method: "PATCH", headers: { "Content-Type": "application/json", Authorization: "Bearer " + k }, body: JSON.stringify({ enabled: !t.enabled }) }); await loadSchedTasks(); } catch (e) {}
  }
  async function deleteSchedTask(id) {
    const k = providers.gateway.apiKey; if (!k) return;
    try { await fetch(kbBase() + "/v1/scheduled/" + id, { method: "DELETE", headers: { Authorization: "Bearer " + k } }); await loadSchedTasks(); } catch (e) {}
  }
  function readDocFile(file) {
    if (!file) return;
    setDocError("");
    const name = file.name || "Belge";
    const ext = (name.match(/\.[^.]+$/) || [""])[0].toLowerCase();
    const serverExtract = ext === ".pdf" || ext === ".docx";
    if (serverExtract && file.size > 10 * 1024 * 1024) {
      setDocError("Dosya çok büyük (max 10 MB)");
      return;
    }
    const fr = new FileReader();
    fr.onerror = () => setDocError("Dosya okunamadı");
    fr.onload = () => {
      if (!docTitle) setDocTitle(name.replace(/\.[^.]+$/, ""));
      if (serverExtract) {
        const raw = String(fr.result || "");
        const b64 = raw.includes(",") ? raw.split(",").pop() : raw;
        setDocFile({ name, mime: file.type || (ext === ".pdf" ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), b64 });
        setDocText("");
      } else {
        setDocFile(null);
        setDocText(String(fr.result || ""));
      }
    };
    if (serverExtract) fr.readAsDataURL(file);
    else fr.readAsText(file);
  }

  // ---- sunucu sohbet listesi → yerel çekmeceye ekle ----
  useEffect(() => {
    if (!hydrated) return;
    const k = providers.gateway.apiKey; if (!k) return;
    (async () => {
      try {
        const r = await fetch(trim(providers.gateway.baseUrl).replace(/\/v1$/, "") + "/v1/conversations", { headers: { Authorization: "Bearer " + k } });
        if (!r.ok) return;
        const items = (await r.json()).data || [];
        setConvs(prev => {
          const have = new Set(prev.map(c => c.serverId).filter(Boolean));
          const add = items.filter(s => !have.has(s.id)).map(s => ({
            id: newId(), serverId: s.id, title: s.title || "Sunucu sohbeti", messages: [], remote: true,
            updatedAt: Date.parse(s.updated_at || s.created_at || "") || Date.now(),
          }));
          return add.length ? [...prev, ...add] : prev;
        });
      } catch (e) {}
    })();
  }, [hydrated, providers.gateway.apiKey]);

  // ---- uzak sohbet açılınca mesajlarını getir ----
  useEffect(() => {
    const a = convs.find(c => c.id === activeId);
    if (!a || !a.remote || !a.serverId || (a.messages && a.messages.length)) return;
    const k = providers.gateway.apiKey; if (!k) return;
    (async () => {
      try {
        const r = await fetch(trim(providers.gateway.baseUrl).replace(/\/v1$/, "") + "/v1/conversations/" + a.serverId, { headers: { Authorization: "Bearer " + k } });
        if (!r.ok) return;
        const d = await r.json();
        const msgs = (d.messages || []).map(m => ({ role: m.role, content: m.content, route: m.route || undefined }));
        setConvs(prev => prev.map(c => (c.id === a.id ? { ...c, messages: msgs, remote: false } : c)));
      } catch (e) {}
    })();
  }, [activeId, providers.gateway.apiKey]);

  function newConv() {
    const id = newId();
    setConvs(prev => [{ id, title: "Yeni sohbet", messages: [], updatedAt: Date.now() }, ...prev]);
    setActiveId(id); setShowChats(false);
  }
  function deleteConv(id) {
    setConvs(prev => {
      const next = prev.filter(c => c.id !== id);
      if (id === activeId) {
        if (next.length) setActiveId(next[0].id);
        else { const nid = newId(); next.unshift({ id: nid, title: "Yeni sohbet", messages: [], updatedAt: Date.now() }); setActiveId(nid); }
      }
      return next;
    });
  }

  // current model + provider resolution
  const curItem = modelId === "custom"
    ? { id: "custom", name: customModel || "Özel model", desc: "ollama", icon: Cpu, provider: "ollama", model: customModel }
    : (MODEL_FLAT.find(m => m.id === modelId) || MODEL_FLAT[0]);
  const curProv = providers[curItem.provider];
  const curApiModel = curItem.model;
  const curEffort = EFFORTS.find(e => e.id === effort);
  const activePersona = PERSONAS.find(p => p.id === personaId) || PERSONAS[0];
  const visionRoute = (gatewayInfo && gatewayInfo.vision) || "VISION_MODEL";
  const imageRouteHint = pending.length > 0 && curItem.provider === "gateway" && curApiModel === "auto"
    ? "Görsel auto route: " + visionRoute
    : "";

  function provReady(id) {
    const p = providers[id];
    if (id === "ollama" || id === "gateway") return !!p.baseUrl;
    if (id === "anthropic") return true;
    return !!p.apiKey;
  }
  const ready = provReady(curItem.provider);

  function buildSystem() {
    const agentLine = agent === "direct" ? "" :
      "Bir " + AGENTS.find(a => a.id === agent).name + " ajanı üzerinden çalışıyorsun; araç çağrıları ve çok adımlı görevler mümkün.";
    const personaPrompt = activePersona.id === "custom" ? customPersona.trim() : activePersona.sys;
    const personaLine = personaPrompt ? ("Seçili persona: " + activePersona.name + ". " + personaPrompt) : "";
    return [
      "Sen NOVA adlı kişisel yapay zeka asistanısın. Türkçe konuşursun.",
      "Teknik, net ve doğrudan ol. Gereksiz dolgu cümleler kurma.",
      curItem.provider === "gateway" ? "Dinamik yönlendirme aktif: göreve en uygun modeli seç." : ("Etkin model: " + curItem.name + "."),
      personaLine, agentLine, curEffort.sys,
      reasoning ? "Yanıttan önce kısa bir muhakeme yapabilirsin ama nihai yanıtı açık ver." : "",
    ].filter(Boolean).join(" ");
  }

  const updateLast = (patch) => setMessages(prev => {
    const c = [...prev]; c[c.length - 1] = { ...c[c.length - 1], ...patch }; return c;
  });

  // Gateway + oturum varsa: aktif sohbeti sunucuda da aç (mesajlar oraya yazılır).
  async function ensureServerConv() {
    if (curItem.provider !== "gateway" || !providers.gateway.apiKey) return null;
    const a = convs.find(c => c.id === activeId);
    if (!a) return null;
    if (a.serverId) return a.serverId;
    try {
      const r = await fetch(trim(providers.gateway.baseUrl).replace(/\/v1$/, "") + "/v1/conversations", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: "Bearer " + providers.gateway.apiKey },
        body: JSON.stringify({ title: (a.title && a.title !== "Yeni sohbet") ? a.title : (((a.messages[0] || {}).content) || "Yeni sohbet").slice(0, 60) }),
      });
      if (!r.ok) return null;
      const d = await r.json();
      setConvs(prev => prev.map(c => (c.id === activeId ? { ...c, serverId: d.id } : c)));
      return d.id;
    } catch (e) { return null; }
  }

  // çekirdek: verili geçmişe göre asistan yanıtı üretir (stream)
  async function complete(historyMsgs) {
    setMessages(prev => [...prev, { role: "assistant", content: "", thinking: reasoning, thoughts: "", at: Date.now() }]);
    setBusy(true);
    const ctrl = new AbortController(); abortRef.current = ctrl;
    let full = "", route = null, thoughts = "", toolSteps = [];
    const t0 = performance.now();
    let firstAt = 0;                            // ilk token'a kadar geçen süre (TTFT)
    const convId = await ensureServerConv();   // sunucu geçmişi (varsa)
    try {
      await streamChat({
        prov: curProv, model: curApiModel, system: buildSystem(),
        history: historyMsgs.map(m => ({ role: m.role, content: m.content, images: m.images })),
        think: reasoning, signal: ctrl.signal,
        extra: curItem.provider === "gateway" ? { effort, think: reasoning, ...(agentMode ? { agent: true } : {}), ...(convId ? { conversation_id: convId } : {}) } : {},
        onRoute: (r) => { route = r; },
        onThought: (t) => { thoughts += t; updateLast({ thoughts }); },
        onTool: (s) => {
          if (s.done) {
            const next = [...toolSteps];
            let idx = -1;
            for (let i = next.length - 1; i >= 0; i--) { if (next[i].name === s.name) { idx = i; break; } }
            if (idx >= 0) next[idx] = { ...next[idx], ...s, q: next[idx].q || s.q };
            else next.push(s);
            toolSteps = next;
          } else {
            toolSteps = [...toolSteps, s];
          }
          updateLast({ tools: toolSteps });
        },
        onToken: (t) => { if (!firstAt) firstAt = performance.now() - t0; full += t; updateLast({ content: full, route }); },
      });
      const ms = Math.round(performance.now() - t0);
      const tok = Math.max(1, Math.round((full.length + thoughts.length) / 4)); // ~4 krktr/token
      updateLast({ content: full.trim() || "(boş yanıt)", route, stats: { ms, ttft: Math.round(firstAt), tok, model: route || curApiModel } });
      const site = extractWebsite(full);
      if (site) openArtifact({ type: "html", code: site, lang: "html" });   // tam HTML sayfası → canlı önizlemeyi otomatik aç
    } catch (e) {
      const h = errHint(e, curProv);
      updateLast({ content: full + (h ? (full ? "\n\n" : "") + h : ""), route });
    } finally { setBusy(false); abortRef.current = null; }
  }

  async function sendChat(textArg) {
    const text = (textArg != null ? textArg : input).trim();
    const imgs = pending;
    if ((!text && !imgs.length) || busy) return;
    setInput(""); setPending([]);
    const userMsg = { role: "user", content: text };
    if (imgs.length) userMsg.images = imgs;
    // Görselleri MinIO'ya da arşivle (/v1/media) — model çağrısı data URL ile sürer.
    if (imgs.length && providers.gateway.apiKey) {
      const base = trim(providers.gateway.baseUrl).replace(/\/v1$/, "");
      imgs.forEach(u => {
        const du = typeof u === "string" ? u : ((u && u.url) || "");
        if (!du.startsWith("data:")) return;
        fetch(base + "/v1/media", {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: "Bearer " + providers.gateway.apiKey },
          body: JSON.stringify({ data_url: du }),
        }).catch(() => {});
      });
    }
    const hist = [...messages, userMsg];
    setMessages(hist);
    await complete(hist);
  }

  async function regenerate() {
    if (busy) return;
    const lastUser = [...messages].reverse().find(m => m.role === "user");
    if (!lastUser) return;
    const base = [...messages];
    while (base.length && base[base.length - 1].role === "assistant") base.pop();
    setMessages(base);
    await complete(base);
  }

  function copyText(t) { try { navigator.clipboard && navigator.clipboard.writeText(t); } catch (e) {} }

  // ---- dosya indirme (artifact + sohbet dışa aktarma) ----
  function download(filename, content, mime = "text/plain") {
    try {
      const blob = new Blob([content], { type: mime + ";charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a"); a.href = url; a.download = filename; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    } catch (e) {}
  }
  function escapeHtml(s = "") {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }
  // artifact iframe içeriği: html/svg doğrudan, mermaid parent uygulamada SVG'ye render edilir.
  function artifactSrcDoc(a) {
    if (!a) return "";
    const code = String(a.code || "");
    const empty = !code.trim() && !a.renderedSvg;
    if (empty) {
      return `<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;background:#0b0e16;color:#d7f6ff;font:13px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;display:grid;place-items:center;min-height:100vh;padding:24px;}div{border:1px solid rgba(255,255,255,.12);border-radius:12px;padding:16px 18px;background:rgba(255,255,255,.05);}</style></head><body><div>Önizlenecek içerik yok.</div></body></html>`;
    }
    if (a.type === "mermaid") {
      if (a.renderedSvg) {
        return `<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;background:#0b0e16;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;}svg{max-width:100%;height:auto;}</style></head><body>${a.renderedSvg}</body></html>`;
      }
      return `<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;background:#0b0e16;color:#d7f6ff;font:13px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;padding:20px;}strong{display:block;color:#ffb86b;margin-bottom:12px;}pre{white-space:pre-wrap;word-break:break-word;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);border-radius:10px;padding:14px;}</style></head><body><strong>Mermaid render edilemedi${a.error ? ": " + escapeHtml(a.error) : ""}</strong><pre>${escapeHtml(code)}</pre></body></html>`;
    }
    if (a.type === "svg") return `<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;background:#fff;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:18px;overflow:auto;}svg{max-width:100%;height:auto;}</style></head><body>${code}</body></html>`;
    if (/^\s*(<!doctype|<html[\s>])/i.test(code)) return code;
    return `<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;min-height:100vh;padding:18px;background:#fff;color:#111;font:14px/1.5 system-ui,-apple-system,Segoe UI,sans-serif;}</style></head><body>${code}</body></html>`;
  }
  async function openArtifact(next) {
    if (!next || next.type !== "mermaid") { setArtifact(next); return; }
    try {
      const id = "nova-mermaid-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 7);
      const mermaid = await loadMermaid();
      const out = await mermaid.render(id, next.code);
      setArtifact({ ...next, renderedSvg: out.svg });
    } catch (e) {
      setArtifact({ ...next, error: (e && e.message) || String(e) });
    }
  }
  function artifactSandbox(a) {
    // HTML site preview: allow scripts so pages are interactive — but NEVER
    // allow-same-origin, so the iframe runs in a null origin and cannot reach
    // the app, its storage, cookies or gateway token. SVG/Mermaid stay locked.
    return a && a.type === "html" ? "allow-scripts" : "";
  }
  // extractWebsite → ./lib/site.mjs (saf, test edilebilir; web/test/site.test.mjs)
  function exportChat(fmt) {
    const a = convs.find(c => c.id === activeId); if (!a) return;
    const ts = new Date().toISOString().slice(0, 16).replace(/[:T]/g, "-");
    const safe = (a.title || "nova-sohbet").replace(/[^\wçğışöü -]/gi, "").slice(0, 40).trim() || "nova-sohbet";
    if (fmt === "json") { download(`${safe}-${ts}.json`, JSON.stringify({ title: a.title, messages: a.messages }, null, 2), "application/json"); return; }
    if (fmt === "pdf") { printChat(a, safe, ts); return; }
    const md = `# ${a.title || "NOVA Sohbet"}\n\n` + (a.messages || []).map(m =>
      `**${m.role === "user" ? "Sen" : "NOVA"}**${m.route ? " · `" + m.route + "`" : ""}:\n\n${m.content || ""}`).join("\n\n---\n\n");
    download(`${safe}-${ts}.md`, md, "text/markdown");
  }
  async function shareChat() {
    const a = convs.find(c => c.id === activeId); if (!a) return;
    const ts = new Date().toISOString().slice(0, 16).replace(/[:T]/g, "-");
    const safe = (a.title || "nova-sohbet").replace(/[^\wçğışöü -]/gi, "").slice(0, 40).trim() || "nova-sohbet";
    const payload = sharePayload(a);
    const encoded = b64urlEncodeText(JSON.stringify(payload));
    if (encoded.length > MAX_SHARE_CHARS) {
      download(`${safe}-${ts}-share.json`, JSON.stringify(payload, null, 2), "application/json");
      setShareNote("JSON");
      setTimeout(() => setShareNote(""), 1800);
      return;
    }
    const url = window.location.href.split("#")[0] + SHARE_HASH + encoded;
    try {
      await navigator.clipboard.writeText(url);
      setShareNote("Kopyalandı");
    } catch (e) {
      try { window.prompt("Paylaşılabilir local link", url); setShareNote("Hazır"); } catch (err) {}
    }
    setTimeout(() => setShareNote(""), 1800);
  }
  function printChat(conv, safe, ts) {
    const rows = (conv.messages || []).map((m, i) => {
      const who = m.role === "user" ? "Sen" : "NOVA";
      const route = m.route ? `<span class="route">${escapeHtml(m.route)}</span>` : "";
      const imgs = (m.images || []).map(src => `<img src="${escapeHtml(src)}" alt="ek ${i + 1}" />`).join("");
      return `<section class="msg ${m.role === "user" ? "user" : "assistant"}"><h2>${who}${route}</h2>${imgs ? `<div class="imgs">${imgs}</div>` : ""}<pre>${escapeHtml(m.content || "")}</pre></section>`;
    }).join("");
    const html = `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(conv.title || "NOVA Sohbet")}</title><style>
      @page{margin:18mm;}*{box-sizing:border-box;}body{margin:0;color:#17202a;background:#fff;font:14px/1.55 system-ui,-apple-system,Segoe UI,sans-serif;}
      header{border-bottom:1px solid #d8dee8;margin-bottom:18px;padding-bottom:12px;}h1{font-size:22px;margin:0 0 6px;}header p{margin:0;color:#687386;font-size:12px;}
      .msg{break-inside:avoid;border:1px solid #e4e8f0;border-radius:10px;padding:14px;margin:0 0 14px;background:#fff;}
      .msg.user{background:#f7fbff}.msg h2{font-size:13px;margin:0 0 8px;color:#0f172a;display:flex;gap:8px;align-items:center}.route{font-size:10px;color:#64748b;font-weight:500}
      pre{white-space:pre-wrap;word-break:break-word;margin:0;font:13px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace}.imgs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px}.imgs img{max-width:220px;max-height:180px;border:1px solid #d8dee8;border-radius:8px}
    </style></head><body><header><h1>${escapeHtml(conv.title || "NOVA Sohbet")}</h1><p>NOVA sohbet dışa aktarımı · ${escapeHtml(ts)} · ${escapeHtml(safe)}.pdf</p></header>${rows || "<p>Boş sohbet.</p>"}<script>window.onload=()=>setTimeout(()=>window.print(),120);<\/script></body></html>`;
    const w = window.open("", "_blank", "width=900,height=1000");
    if (!w) { download(`${safe}-${ts}.html`, html, "text/html"); return; }
    try { w.opener = null; } catch (e) {}
    w.document.open(); w.document.write(html); w.document.close();
  }

  function stopChat() { if (abortRef.current) abortRef.current.abort(); }

  // ---- gerçek ses: analizör (mikrofon/TTS genliği → orb) ----
  function ensureCtx() {
    if (!audioCtxRef.current) { const AC = window.AudioContext || window.webkitAudioContext; audioCtxRef.current = new AC(); }
    if (audioCtxRef.current.state === "suspended") audioCtxRef.current.resume();
    return audioCtxRef.current;
  }
  function runMeter(analyser) {
    const buf = new Uint8Array(analyser.fftSize);
    const loop = () => {
      analyser.getByteTimeDomainData(buf);
      let sum = 0; for (let i = 0; i < buf.length; i++) { const v = (buf[i] - 128) / 128; sum += v * v; }
      const rms = Math.sqrt(sum / buf.length);
      extLevelRef.current = Math.min(1, rms * 2.6);   // gerçek dalga seviyesi
      meterRafRef.current = requestAnimationFrame(loop);
    };
    loop();
  }
  function stopMeter() { if (meterRafRef.current) cancelAnimationFrame(meterRafRef.current); meterRafRef.current = null; extLevelRef.current = -1; }

  function voiceAuthHeaders() {
    return providers.gateway && providers.gateway.apiKey ? { Authorization: "Bearer " + providers.gateway.apiKey } : {};
  }
  function voiceJobBase() {
    return (voiceCfg.jobUrl || "/v1/voice/jobs").replace(/\/+$/, "");
  }
  async function runVoiceJob(type, payload, label) {
    const base = voiceJobBase();
    const auth = voiceAuthHeaders();
    const start = await fetch(base, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...auth },
      body: JSON.stringify({ type, ...payload }),
    });
    if (!start.ok) throw new Error("voice job " + start.status);
    const first = await start.json();
    const id = first.id;
    const started = Date.now();
    while (Date.now() - started < 120000) {
      const r = await fetch(base + "/" + encodeURIComponent(id), { headers: auth });
      if (!r.ok) throw new Error("voice job status " + r.status);
      const job = await r.json();
      const state = job.state === "waiting" ? "kuyrukta" : job.state === "active" ? "çalışıyor" : job.state;
      setVoiceSub((label || "Ses işi") + " · " + state);
      if (job.state === "completed") return job.result || {};
      if (job.state === "failed") throw new Error(job.error || "voice job failed");
      await delay(800);
    }
    throw new Error("voice job timeout");
  }

  function speakBrowser(text) {
    const synth = window.speechSynthesis;
    if (!synth) { setTimeout(() => { setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); }, 2200); return; }
    try {
      const u = new SpeechSynthesisUtterance(text);
      u.lang = "tr-TR"; u.rate = 1.02;
      const trv = synth.getVoices().find(v => v.lang && v.lang.toLowerCase().startsWith("tr"));
      if (trv) u.voice = trv;
      u.onend = () => { setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); };
      synth.cancel(); synth.speak(u);
      setTimeout(() => { if (voiceStateRef.current === "speaking") { setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); } }, Math.min(20000, 2500 + text.length * 55));
    } catch (e) { setTimeout(() => setVoiceState("idle"), 2200); }
  }

  // gateway TTS → ses bytes → Web Audio (orb gerçek dalgaya tepki verir)
  async function speakReal(text) {
    try {
      let ab;
      if (voiceCfg.queued) {
        const result = await runVoiceJob("tts", { input: text, voice: voiceCfg.voice }, "TTS kuyruğu");
        if (!result.audio) throw new Error("tts job returned no audio");
        ab = b64ToArrayBuffer(result.audio);
      } else {
        const auth = voiceAuthHeaders();
        const r = await fetch(voiceCfg.ttsUrl, {
          method: "POST", headers: { "Content-Type": "application/json", ...auth },
          body: JSON.stringify({ input: text, voice: voiceCfg.voice }),
        });
        if (!r.ok) throw new Error("tts " + r.status);
        ab = await r.arrayBuffer();
      }
      const ctx = ensureCtx();
      const audioBuf = await ctx.decodeAudioData(ab);
      const src = ctx.createBufferSource(); src.buffer = audioBuf;
      const analyser = ctx.createAnalyser(); analyser.fftSize = 512;
      src.connect(analyser); analyser.connect(ctx.destination);
      ttsAudioRef.current = src;
      src.onended = () => { stopMeter(); ttsAudioRef.current = null; setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); };
      runMeter(analyser); src.start();
    } catch (e) {
      stopMeter();
      speakBrowser(text);   // gateway/TTS yoksa tarayıcı TTS'e düş
    }
  }

  function speak(text) {
    setVoiceState("speaking"); setVoiceSub(text.slice(0, 180));
    if (voiceCfg.real) speakReal(text); else speakBrowser(text);
  }

  // Sesi anında kes (Web Audio TTS + tarayıcı TTS) — barge-in / "Durdur".
  function stopSpeaking() {
    try { ttsAudioRef.current && ttsAudioRef.current.stop(); } catch (e) {}
    ttsAudioRef.current = null;
    try { window.speechSynthesis && window.speechSynthesis.cancel(); } catch (e) {}
    stopMeter();
    setVoiceState("idle");
    setVoiceSub("Konuşmak için mikrofona dokun");
  }

  async function runVoice(text) {
    setVoiceState("thinking"); setVoiceSub("Düşünüyor…");
    const hist = [...messages, { role: "user", content: text }];
    setMessages(hist);
    let full = "";
    const vConvId = await ensureServerConv();   // sunucu geçmişi (varsa)
    try {
      await streamChat({
        prov: curProv, model: curApiModel, system: buildSystem(),
        history: hist.map(m => ({ role: m.role, content: m.content, images: m.images })),
        think: reasoning,
        extra: curItem.provider === "gateway" ? { effort, think: reasoning, ...(agentMode ? { agent: true } : {}), ...(vConvId ? { conversation_id: vConvId } : {}) } : {},
        onToken: (t) => { full += t; },
      });
    } catch (e) { full = errHint(e, curProv) || "Yanıt alınamadı."; }
    const reply = full.trim() || "Yanıt alınamadı.";
    setMessages(prev => [...prev, { role: "assistant", content: reply }]);
    speak(reply);
  }

  // gerçek mikrofon kaydı → Whisper STT (gateway) → runVoice
  async function startListeningReal() {
    if (voiceState === "listening") {
      try { mediaRecRef.current && mediaRecRef.current.stop(); } catch (e) {}
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;
      const ctx = ensureCtx();
      const srcNode = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser(); analyser.fftSize = 512;
      srcNode.connect(analyser); runMeter(analyser);     // mikrofon genliği → orb
      const mime = MediaRecorder.isTypeSupported("audio/webm") ? "audio/webm" : "";
      const rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
      mediaRecRef.current = rec;
      const chunks = [];
      rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
      rec.onstop = async () => {
        stopMeter();
        stream.getTracks().forEach(t => t.stop()); mediaStreamRef.current = null;
        const blob = new Blob(chunks, { type: mime || "audio/webm" });
        setVoiceState("thinking"); setVoiceSub("Çözümleniyor…");
        try {
          const b64 = await blobToB64(blob);
          let d;
          if (voiceCfg.queued) {
            d = await runVoiceJob("stt", { audio: b64, mime: blob.type, language: "tr" }, "STT kuyruğu");
          } else {
            const auth = voiceAuthHeaders();
            const r = await fetch(voiceCfg.sttUrl, {
              method: "POST", headers: { "Content-Type": "application/json", ...auth },
              body: JSON.stringify({ audio: b64, mime: blob.type, language: "tr" }),
            });
            d = await r.json();
          }
          const text = (d.text || "").trim();
          if (text) runVoice(text);
          else { setVoiceState("idle"); setVoiceSub("Ses çözülemedi — tekrar dene"); }
        } catch (e) { setVoiceState("idle"); setVoiceSub("Whisper'a ulaşılamadı — gateway açık mı?"); }
      };
      rec.start();
      setVoiceState("listening"); setVoiceSub("Dinliyorum… (bitirmek için tekrar dokun)");
    } catch (e) {
      setVoiceState("idle"); setVoiceSub("Mikrofon izni yok — bu ortamda kapalı olabilir");
    }
  }

  function startListening() {
    if (voiceCfg.real) return startListeningReal();
    if (voiceState === "listening") {
      try { recogRef.current && recogRef.current.stop(); } catch (e) {}
      setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); return;
    }
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) { setVoiceState("listening"); setVoiceSub("Mikrofon bu ortamda kapalı — aşağıdan yazabilirsin"); return; }
    try {
      const rec = new SR(); rec.lang = "tr-TR"; rec.interimResults = true; rec.continuous = false;
      recogRef.current = rec; setVoiceState("listening"); setVoiceSub("Dinliyorum…");
      let final = "";
      rec.onresult = (ev) => {
        let interim = "";
        for (let i = ev.resultIndex; i < ev.results.length; i++) {
          const r = ev.results[i]; if (r.isFinal) final += r[0].transcript; else interim += r[0].transcript;
        }
        setVoiceSub((final + interim) || "Dinliyorum…");
      };
      rec.onerror = () => { setVoiceState("listening"); setVoiceSub("Mikrofon erişimi yok — aşağıdan yazabilirsin"); };
      rec.onend = () => {
        if (final.trim()) runVoice(final.trim());
        else if (voiceStateRef.current === "listening") { setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); }
      };
      rec.start();
    } catch (e) { setVoiceState("listening"); setVoiceSub("Mikrofon başlatılamadı — aşağıdan yazabilirsin"); }
  }

  function submitVoiceText() { const t = voiceText.trim(); if (!t) return; setVoiceText(""); runVoice(t); }

  const voiceLabel = { idle: "Hazır", listening: "Dinliyorum", thinking: "Düşünüyorum", speaking: "Konuşuyorum" }[voiceState];
  const SUGGESTIONS = [
    { icon: Cpu,      cat: "Kod",      t: "Spring Boot REST endpoint örneği yaz", d: "JWT auth + validation ile" },
    { icon: Waves,    cat: "Güvenlik", t: "Bir log satırında IOC ara ve açıkla", d: "SOC/DFIR bakışıyla" },
    { icon: Activity, cat: "Analiz",   t: "Qwen3 14B vs Gemma 4 karşılaştır", d: "yerel kullanım için" },
    { icon: Brain,    cat: "Fikir",    t: "RSS haber dedup mantığı öner", d: "embedding tabanlı" },
  ];
  const hour = new Date().getHours();
  const greet = hour < 6 ? "İyi geceler" : hour < 12 ? "Günaydın" : hour < 18 ? "İyi günler" : "İyi akşamlar";
  const who = auth && auth.email ? ", " + auth.email.split("@")[0] : "";

  const convFilter = (c) => !convSearch.trim() || (c.title || "").toLowerCase().includes(convSearch.trim().toLowerCase());

  return (
    <div className="vega-root has-rail" onClick={() => setOpenDD(null)}>
      <style>{STYLES}</style>
      <div className="aurora"><div className="blob b1"/><div className="blob b2"/><div className="blob b3"/></div>
      <div className="grid-overlay" /><div className="grain" />

      {/* Kalıcı sol panel (geniş ekran) */}
      <aside className="side-rail" onClick={(e)=>e.stopPropagation()}>
        <div className="rail-brand"><div className="brand-mark" style={{width:30,height:30,borderRadius:9}}><Sparkles size={15} color="#fff" /></div> <span>NOVA</span></div>
        <button className="new-chat" onClick={newConv}><Plus size={16} /> Yeni sohbet</button>
        <div className="rail-search"><input value={convSearch} onChange={(e)=>setConvSearch(e.target.value)} placeholder="Sohbetlerde ara…" />{convSearch && <button onClick={()=>setConvSearch("")}><X size={13} /></button>}</div>
        <div className="rail-list">
          {convs.filter(convFilter).map(c => (
            <div key={c.id} className={"conv-row" + (c.id===activeId?" on":"")} onClick={()=>{ setActiveId(c.id); setMode("chat"); }}>
              <MessageSquare size={14} />
              <span className="cr-title">{c.title || "Yeni sohbet"}</span>
              {c.serverId && <Cloud size={12} className="cr-cloud" title="Sunucuya senkron" />}
              <button className="cr-del" onClick={(e)=>{ e.stopPropagation(); deleteConv(c.id); }}><Trash2 size={13} /></button>
            </div>
          ))}
          {convs.filter(convFilter).length === 0 && <div className="rail-empty">Sonuç yok</div>}
        </div>
        {messages.length > 0 && (
          <div className="rail-export">
            <span>Dışa aktar:</span>
            <button onClick={()=>exportChat("md")} title="Markdown indir"><Download size={13} /> MD</button>
            <button onClick={()=>exportChat("json")} title="JSON indir"><Download size={13} /> JSON</button>
            <button onClick={()=>exportChat("pdf")} title="PDF olarak yazdır/kaydet"><Download size={13} /> PDF</button>
            <button onClick={shareChat} title="Paylaşılabilir local link kopyala"><Link2 size={13} /> {shareNote || "Link"}</button>
          </div>
        )}
        <button className="rail-settings" onClick={(e)=>{e.stopPropagation(); setShowSettings(true);}}><Settings size={15} /> Ayarlar{auth ? " · " + (auth.email||"").split("@")[0] : ""}</button>
      </aside>

      <header className="topbar">
        <div className="brand">
          <button className="icon-btn rail-hide" onClick={(e)=>{e.stopPropagation(); setShowChats(true);}} title="Sohbetler"><Menu size={18} /></button>
          <div className="brand-mark"><Sparkles size={17} color="#fff" /></div>
          <div className="brand-text">
            <h1>NOVA</h1>
            <div className="sub">{curItem.name} · {activePersona.short} · <b>{curEffort.name}</b>{reasoning ? " · düşünme" : ""}</div>
          </div>
        </div>
        <div className="topright">
          <div className="hdr-model">
            <button className={"status-pill clickable" + (openDD==="hmodel"?" open":"")} onClick={(e)=>{e.stopPropagation(); setOpenDD(openDD==="hmodel"?null:"hmodel");}} title="Modeli değiştir">
              <span className={"dot" + (ready ? "" : " off")} />
              {curItem.name}{curApiModel ? " · " + curApiModel : ""}
              <ChevronDown size={13} style={{marginLeft:2,opacity:.6}} />
            </button>
            {openDD==="hmodel" && (
              <div className="hdr-dd" onClick={(e)=>e.stopPropagation()}>
                {MODELS.map(g => (
                  <div key={g.group}>
                    <div className="dd-group-label">{g.group}</div>
                    {g.items.map(it => (
                      <div key={it.id} className={"dd-item" + (it.id===modelId?" sel":"")} onClick={()=>{ setModelId(it.id); setOpenDD(null); }}>
                        <div className="di-ic">{React.createElement(it.icon,{size:16})}</div>
                        <div className="di-txt"><div className="t">{it.name}</div><div className="d">{it.desc}</div></div>
                        {it.id===modelId && <Check size={16} className="di-check" />}
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </div>
          <button className="icon-btn" onClick={(e)=>{e.stopPropagation(); setShowSettings(true);}}><Settings size={18} /></button>
        </div>
      </header>

      {showChats && (
        <>
          <div className="drawer-overlay" onClick={()=>setShowChats(false)} />
          <div className="drawer" onClick={(e)=>e.stopPropagation()}>
            <div className="drawer-head">
              <div className="dh-title">Sohbetler</div>
              <button className="icon-btn" style={{width:34,height:34}} onClick={()=>setShowChats(false)}><X size={16} /></button>
            </div>
            <button className="new-chat" onClick={newConv}><Plus size={16} /> Yeni sohbet</button>
            <div className="conv-list">
              {convs.map(c => (
                <div key={c.id} className={"conv-row" + (c.id===activeId?" on":"")} onClick={()=>{ setActiveId(c.id); setShowChats(false); }}>
                  <MessageSquare size={14} />
                  <span className="cr-title">{c.title || "Yeni sohbet"}</span>
                  {c.serverId && <Cloud size={12} className="cr-cloud" title="Sunucuya senkron" />}
                  <button className="cr-del" onClick={(e)=>{ e.stopPropagation(); deleteConv(c.id); }}><Trash2 size={14} /></button>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      <main className="stage">
        {mode === "voice" ? (
          <div className="voice-view">
            <div className="orb-wrap"><canvas ref={canvasRef} className="orb-canvas" /></div>
            <div className="voice-status">
              <div className="vs-label">{voiceLabel}</div>
              <div className="vs-sub">{voiceSub}</div>
            </div>
            <div className="wavebar" ref={waveRef}>
              {Array.from({ length: 28 }).map((_, i) => <span key={i} style={{ height: 6 }} />)}
            </div>
            <div className="voice-controls">
              <button
                className={"mic-btn" + (voiceState === "listening" ? " active" : "") + (voiceState === "speaking" ? " speaking" : "")}
                onClick={voiceState === "speaking" ? stopSpeaking : startListening}
                title={voiceState === "speaking" ? "Durdur" : voiceState === "listening" ? "Dinlemeyi durdur" : "Konuşmak için dokun"}>
                {(voiceState === "speaking" || voiceState === "listening") ? <Square size={24} /> : <Mic size={26} />}
              </button>
              {voiceState === "speaking" && (
                <button className="mini-btn" onClick={stopSpeaking}>
                  <Square size={15} /> Durdur
                </button>
              )}
            </div>
            {(!sttSupported || voiceState === "listening") && (
              <div className="voice-fallback">
                <input value={voiceText} onChange={(e)=>setVoiceText(e.target.value)}
                  onKeyDown={(e)=>{ if(e.key==="Enter") submitVoiceText(); }}
                  placeholder="Mikrofon yoksa buraya yaz, ajan sesli yanıtlasın…" />
                <button className="send-btn" onClick={submitVoiceText} disabled={!voiceText.trim()}><Send size={18} /></button>
              </div>
            )}
          </div>
        ) : (
          <div className="chat-view">
            {messages.length === 0 ? (
              <div className="empty-state">
                <div className="brand-mark" style={{ width: 56, height: 56, borderRadius: 18 }}><Sparkles size={24} color="#fff" /></div>
                <div>
                  <div className="es-title">{greet}{who}, ben <em>NOVA</em></div>
                  <div className="es-sub">Kişisel ajanın. Şu an <b>{curItem.name}</b>{curApiModel ? " · " + curApiModel : ""} ile hazırım. Bir kartla başla ya da alttan yaz.</div>
                </div>
                <div className="sugg-grid">
                  {SUGGESTIONS.map((s,i)=>(
                    <button key={i} className="sugg-card" onClick={()=>sendChat(s.t)}>
                      <div className="sc-ic">{React.createElement(s.icon,{size:17})}</div>
                      <div className="sc-tx"><div className="sc-cat">{s.cat}</div><div className="sc-t">{s.t}</div><div className="sc-d">{s.d}</div></div>
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="chat-scroll" ref={scrollRef}>
                {messages.map((m, idx) => {
                  const isLast = idx === messages.length - 1;
                  const isAI = m.role === "assistant";
                  return (
                    <div key={idx}>
                      {isAI && m.tools && m.tools.length > 0 && (
                        <div className="msg" style={{ marginLeft: 47, marginBottom: -4 }}>
                          <div className="tool-trace">
                            <div className="tt-head"><Waves size={13} /> Araç kullanıldı</div>
                            {m.tools.map((s, ti) => (
                              <div key={ti} className="tt-step">
                                <span className="tt-ic">{s.name === "web_search" ? <GitBranch size={12} /> : s.name === "calculator" ? <Activity size={12} /> : s.name === "code_run" ? <Code2 size={12} /> : <Check size={12} />}</span>
                                <span className="tt-name">{s.name === "web_search" ? "Web araması" : s.name === "calculator" ? "Hesaplama" : s.name === "current_time" ? "Saat" : s.name === "code_run" ? "Kod sandbox" : s.name}</span>
                                {s.q && <span className="tt-q">{s.q}</span>}
                                {s.sources && s.sources.length > 0 && (
                                  <div className="tt-sources">
                                    {s.sources.slice(0, 4).map((src, si) => {
                                      const label = `${src.n ? "[" + src.n + "] " : ""}${src.title || src.url || "Kaynak"}${src.score ? " · " + Math.round(src.score * 100) + "%" : ""}`;
                                      return src.url
                                        ? <a key={si} className="tt-source" href={src.url} target="_blank" rel="noreferrer">{label}</a>
                                        : <span key={si} className="tt-source">{label}</span>;
                                    })}
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                      {isAI && m.thinking && (
                        <div className="msg" style={{ marginLeft: 47, marginBottom: -6 }}><ThinkTrace text={m.thoughts} live={busy && isLast} /></div>
                      )}
                      <div className={"msg " + (m.role === "user" ? "user" : "")}>
                        <div className={"avatar " + (m.role === "user" ? "me" : "ai") + (isAI && !m.content && busy && isLast ? " thinking" : "")}>
                          {m.role === "user" ? "S" : <Sparkles size={16} />}
                        </div>
                        <div className={"bubble " + (m.role === "user" ? "me" : "ai")}>
                          {m.images && m.images.length > 0 && (
                            <div className="msg-imgs">{m.images.map((u, ii) => <img key={ii} className="msg-img" src={u} alt="" />)}</div>
                          )}
                          {m.content
                            ? (isAI ? <Markdown text={m.content} onArtifact={openArtifact} /> : m.content)
                            : (!m.images
                                ? (isAI && busy && isLast
                                    ? <div className="gem-wait">
                                        <div className="gem-status"><span className="gem-orb" /> {m.thoughts ? "Düşünüyor…" : "NOVA yanıt hazırlıyor…"}</div>
                                        <div className="gem-line l1" /><div className="gem-line l2" /><div className="gem-line l3" />
                                      </div>
                                    : <span className="typing"><span/><span/><span/></span>)
                                : null)}
                        </div>
                      </div>
                      {isAI && (m.stats || m.route) && !(busy && isLast) && (
                        <div className="stat-row">
                          <span className="stat-chip model"><GitBranch size={11} /> {(m.stats && m.stats.model) || m.route}</span>
                          {m.stats && (<>
                            <span className="stat-chip"><Activity size={11} /> {m.stats.ms >= 1000 ? (m.stats.ms/1000).toFixed(1)+" sn" : m.stats.ms+" ms"}</span>
                            {m.stats.ttft > 0 && <span className="stat-chip" title="İlk token süresi">⚡ {m.stats.ttft >= 1000 ? (m.stats.ttft/1000).toFixed(1)+"sn" : m.stats.ttft+"ms"}</span>}
                            <span className="stat-chip">~{fmtNum(m.stats.tok)} tok</span>
                            {m.stats.ms > 0 && <span className="stat-chip">{Math.round(m.stats.tok/(m.stats.ms/1000))} tok/sn</span>}
                          </>)}
                          {m.at && <span className="stat-chip time">{new Date(m.at).toLocaleTimeString("tr-TR",{hour:"2-digit",minute:"2-digit"})}</span>}
                        </div>
                      )}
                      {isAI && m.content && !(busy && isLast) && (
                        <div className="msg-actions">
                          <button className="msg-act" onClick={()=>copyText(m.content)}><Copy size={13} /> Kopyala</button>
                          {isLast && <button className="msg-act" onClick={regenerate}><RotateCcw size={13} /> Yeniden</button>}
                        </div>
                      )}
                      {/* gateway health moved to provider settings
                        <div className="kb-hint" style={{margin:"8px 0 0"}}>
                          Default: <b>{gatewayInfo.default || "?"}</b> Â· GÃ¶rsel: <b>{gatewayInfo.vision || "?"}</b>
                        </div>
                      */}
                    </div>
                  );
                })}
              </div>
            )}
            <div className="composer">
              {pending.length > 0 && (
                <div className="attach-strip">
                  {imageRouteHint && <div className="kb-hint" style={{width:"100%",marginBottom:0}}>{imageRouteHint}</div>}
                  {pending.map((u, i) => (
                    <div className="attach-thumb" key={i}>
                      <img src={u} alt="" />
                      <button onClick={()=>setPending(p => p.filter((_, j) => j !== i))}><X size={11} /></button>
                    </div>
                  ))}
                </div>
              )}
              <div className="composer-inner">
                <input ref={fileRef} type="file" accept="image/*" multiple style={{ display: "none" }}
                  onChange={(e)=>{ addImages(e.target.files); e.target.value = ""; }} />
                <button className="attach-btn" onClick={()=>fileRef.current && fileRef.current.click()} title="Görsel ekle"><Plus size={18} /></button>
                <textarea rows={1} value={input}
                  data-gramm="false" data-gramm_editor="false" data-enable-grammarly="false"
                  onChange={(e)=>{ setInput(e.target.value); e.target.style.height="auto"; e.target.style.height=Math.min(e.target.scrollHeight,120)+"px"; }}
                  onKeyDown={(e)=>{ if(e.key==="Enter" && !e.shiftKey){ e.preventDefault(); sendChat(); } }}
                  placeholder={"NOVA'ya yaz… (" + (curApiModel || "model seç") + ")"} />
                {busy
                  ? <button className="send-btn stop" onClick={stopChat}><Square size={17} /></button>
                  : <button className="send-btn" onClick={()=>sendChat()} disabled={!input.trim() && !pending.length}><Send size={18} /></button>}
              </div>
            </div>
          </div>
        )}
      </main>

      {artifact && (
        <div className="artifact-panel" onClick={(e)=>e.stopPropagation()}>
          <div className="ap-head">
            <div className="ap-title"><Code2 size={15} /> Önizleme · <span className="ap-type">{artifact.lang || artifact.type}</span>{artifact.error && <span className="ap-warn">hata</span>}</div>
            <div className="ap-actions">
              <button className="ap-btn" onClick={()=>copyText(artifact.code)} title="Kopyala"><Copy size={14} /></button>
              <button className="ap-btn" onClick={()=>download("nova-artifact." + (artifact.type==="mermaid"?"mmd":artifact.type==="svg"?"svg":"html"), artifact.code, "text/plain")} title="İndir"><Download size={14} /></button>
              <button className="ap-btn" onClick={()=>setArtifact(null)} title="Kapat"><X size={15} /></button>
            </div>
          </div>
          {artifact.type === "html" && (
            <div className="ap-browser">
              <span className="ap-dot r" /><span className="ap-dot y" /><span className="ap-dot g" />
              <div className="ap-url"><CircleDot size={11} /> localhost · canlı önizleme</div>
            </div>
          )}
          <iframe className="ap-frame" title="artifact" sandbox={artifactSandbox(artifact)} referrerPolicy="no-referrer" srcDoc={artifactSrcDoc(artifact)} />
        </div>
      )}

      <div className="dock" onClick={(e)=>e.stopPropagation()}>
        <div className="dock-group">
          <button className={"mode-tab" + (mode==="voice"?" on":"")} onClick={()=>setMode("voice")}><Mic size={15} /> Sesli</button>
          <button className={"mode-tab" + (mode==="chat"?" on":"")} onClick={()=>setMode("chat")}><MessageSquare size={15} /> Sohbet</button>
        </div>

        <div className="selector">
          <button className={"sel-btn" + (openDD==="model"?" open":"")} onClick={()=>setOpenDD(openDD==="model"?null:"model")}>
            <span className="sel-icon">{React.createElement(curItem.icon, { size: 18 })}</span>
            <span className="sel-meta"><span className="lbl">MODEL</span><span className="val">{curItem.name}</span></span>
            <ChevronDown size={15} className="chev" />
          </button>
          {openDD==="model" && (
            <div className="dropdown">
              {MODELS.map(g => (
                <div key={g.group}>
                  <div className="dd-group-label">{g.group}</div>
                  {g.items.map(it => (
                    <div key={it.id} className={"dd-item" + (it.id===modelId?" sel":"")} onClick={()=>{ setModelId(it.id); setOpenDD(null); }}>
                      <div className="di-ic">{React.createElement(it.icon,{size:16})}</div>
                      <div className="di-txt"><div className="t">{it.name}</div><div className="d">{it.desc}</div></div>
                      {it.id===modelId && <Check size={16} className="di-check" />}
                    </div>
                  ))}
                </div>
              ))}
              <div className="dd-group-label">Özel Yerel Model</div>
              <div className="dd-custom" onClick={(e)=>e.stopPropagation()}>
                <input value={customModel} onChange={(e)=>setCustomModel(e.target.value)}
                  onKeyDown={(e)=>{ if(e.key==="Enter" && customModel.trim()){ setModelId("custom"); setOpenDD(null); } }}
                  placeholder="örn: llama3.1:8b" />
                <button onClick={()=>{ if(customModel.trim()){ setModelId("custom"); setOpenDD(null); } }}>Kullan</button>
              </div>
            </div>
          )}
        </div>

        <div className="effort">
          {EFFORTS.map(e => (
            <button key={e.id} className={"eff-opt" + (e.id===effort?" on":"")} onClick={()=>setEffort(e.id)} title={e.name}>
              {React.createElement(e.icon,{size:14})}<span className="txt">{e.name}</span>
            </button>
          ))}
          <button className={"reason-toggle" + (reasoning?" on":"")} onClick={()=>setReasoning(!reasoning)} title="Düşünme modu">
            <span className="rt-switch" /> Düşünme
          </button>
          <button className={"reason-toggle agent" + (agentMode?" on":"")} onClick={()=>setAgentMode(!agentMode)} title="Ajan: web araması + araçlar (yalnız Gateway+yerel model)">
            <Waves size={13} /> Ajan
          </button>
        </div>
      </div>

      {showSettings && (
        <div className="overlay" onClick={()=>setShowSettings(false)}>
          <div className="modal" onClick={(e)=>e.stopPropagation()}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}}>
              <div>
                <h2><Settings size={19} color="#38e1d6" /> Sağlayıcılar & Ajan</h2>
                <div className="m-sub">API key'li bulut modelleri ve yerel Ollama'yı buradan ayarla.</div>
              </div>
              <button className="icon-btn" onClick={()=>setShowSettings(false)}><X size={18} /></button>
            </div>

            <div className="m-section">
              <div className="callout">
                <b>Önerilen kurulum:</b> Gateway üzerinden bağlan ve <b>Keycloak ile Giriş</b> yap — anahtar yapıştırmaya gerek kalmaz, oturum otomatik tazelenir, sohbet geçmişin sunucuya da yazılır. Ayarlar bu tarayıcıda kalıcıdır (IndexedDB).
              </div>
            </div>

            <div className="m-section">
              <div className="ms-label">Persona / Sistem Prompt</div>
              <div className="persona-grid">
                {PERSONAS.map(p => (
                  <button key={p.id} type="button" className={"persona-card" + (p.id===activePersona.id ? " sel" : "")} onClick={()=>setPersonaId(p.id)}>
                    <span className="pp-ic">{React.createElement(p.icon, { size: 17 })}</span>
                    <span>
                      <span className="pp-t">{p.name}</span>
                      <span className="pp-d">{p.desc}</span>
                    </span>
                    {p.id===activePersona.id && <Check size={16} className="pp-check" />}
                  </button>
                ))}
              </div>
              {activePersona.id === "custom" && (
                <div className="pc-inputs persona-custom">
                  <label>Özel sistem yönergesi</label>
                  <textarea value={customPersona} onChange={(e)=>setCustomPersona(e.target.value)} placeholder="NOVA nasıl davransın? Rol, ton, öncelikler ve kaçınması gereken şeyleri yaz." rows={4} />
                </div>
              )}
            </div>

            {usageInfo && (
              <div className="m-section">
                <div className="ms-label">Kullanım — Bu Ay</div>
                <div className="usage-grid">
                  <div className="usage-stat"><div className="us-v">{fmtNum(usageInfo.month.tokens_in)}</div><div className="us-l">giren token</div></div>
                  <div className="usage-stat"><div className="us-v">{fmtNum(usageInfo.month.tokens_out)}</div><div className="us-l">çıkan token</div></div>
                  <div className="usage-stat"><div className="us-v">{fmtNum(usageInfo.month.requests)}</div><div className="us-l">istek</div></div>
                  <div className="usage-stat"><div className="us-v">${(usageInfo.month.cost_micros / 1e6).toFixed(4)}</div><div className="us-l">maliyet</div></div>
                </div>
                {usageInfo.quota && Number(usageInfo.quota.limit_micros) > 0 && (
                  <div className="quota-wrap">
                    <div className="quota-bar"><span style={{ width: Math.min(100, (100 * Number(usageInfo.quota.used_micros)) / Number(usageInfo.quota.limit_micros)) + "%" }} /></div>
                    <div className="quota-txt">Kota: ${(Number(usageInfo.quota.used_micros) / 1e6).toFixed(2)} / ${(Number(usageInfo.quota.limit_micros) / 1e6).toFixed(2)} · yenileme {new Date(usageInfo.quota.resets_at).toLocaleDateString("tr-TR")}</div>
                  </div>
                )}
                {usageInfo.month.by_model.length > 0 && (
                  <div className="usage-models">
                    {usageInfo.month.by_model.map(m => (
                      <div key={m.model} className="um-row">
                        <span className="um-m">{m.model}</span>
                        <span className="um-t">{fmtNum(Number(m.tokens_in) + Number(m.tokens_out))} tok · {fmtNum(m.requests)} istek</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {providers.gateway.apiKey && (
              <div className="m-section">
                <div className="ms-label">Bilgi Tabanı (Ajan · belgelerle sohbet)</div>
                <div className="kb-hint">Belge yükle; <b>Ajan</b> modunda model bu belgelerde arama yapıp kaynak göstererek cevaplar (doc_search).</div>
                <div className="kb-upload">
                  <input className="kb-title" value={docTitle} onChange={(e)=>setDocTitle(e.target.value)} placeholder="Başlık (opsiyonel)" />
                  <textarea className="kb-text" value={docText} onChange={(e)=>{ setDocText(e.target.value); setDocFile(null); setDocError(""); }} placeholder="Metni yapıştır ya da .txt/.md/.pdf/.docx dosyası seç…" rows={3} />
                  {docFile && <div className="kb-hint">Seçili dosya: <b>{docFile.name}</b> — metin gateway tarafında çıkarılacak.</div>}
                  {docError && <div className="kb-hint" style={{color:"var(--coral)"}}>{docError}</div>}
                  <div className="kb-actions">
                    <input ref={docFileRef} type="file" accept=".txt,.md,.markdown,.csv,.json,.log,.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" style={{display:"none"}} onChange={(e)=>{ readDocFile(e.target.files[0]); e.target.value=""; }} />
                    <button className="kb-file" onClick={()=>docFileRef.current && docFileRef.current.click()}><Plus size={14} /> Dosya</button>
                    <button className="kb-add" onClick={uploadDoc} disabled={docBusy || (!docFile && docText.trim().length<20)}>{docBusy ? "Yükleniyor…" : "Belgeyi Ekle"}</button>
                  </div>
                </div>
                {docs.length > 0 && (
                  <div className="kb-list">
                    {docs.map(d => (
                      <div key={d.id} className="kb-row">
                        <MessageSquare size={13} />
                        <span className="kb-name">{d.title}</span>
                        <span className="kb-meta">{d.chunks} parça</span>
                        <button className="kb-del" onClick={()=>deleteDoc(d.id)}><Trash2 size={13} /></button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {providers.gateway.apiKey && (
              <div className="m-section">
                <div className="ms-label">Zamanlanmış Görevler (Otomatik Ajan)</div>
                <div className="kb-hint">Tekrarlayan ajan görevleri tanımla (ör. her sabah haber özeti). Sunucu zamanı gelince ajanı çalıştırır; son sonuç altta görünür. Gateway'de <b>SCHEDULER_ENABLED=1</b> gerekir.</div>
                <div className="kb-upload">
                  <input className="kb-title" value={schedForm.title} onChange={(e)=>setSchedForm(f=>({...f,title:e.target.value}))} placeholder="Görev başlığı (ör. Günlük teknoloji özeti)" />
                  <textarea className="kb-text" value={schedForm.prompt} onChange={(e)=>setSchedForm(f=>({...f,prompt:e.target.value}))} placeholder="Ajana verilecek görev (ör. 'Bugünün teknoloji haberlerini web'de ara, 5 maddede özetle')" rows={2} />
                  <div className="kb-actions">
                    <select className="kb-title sched-sel" value={schedForm.schedule} onChange={(e)=>setSchedForm(f=>({...f,schedule:e.target.value}))}>
                      <option value="every:30m">Her 30 dakika</option>
                      <option value="every:1h">Her saat</option>
                      <option value="every:6h">Her 6 saat</option>
                      <option value="every:1d">Her gün (24s)</option>
                      <option value="daily:09:00">Her gün 09:00</option>
                      <option value="daily:18:00">Her gün 18:00</option>
                    </select>
                    <button className="kb-add" onClick={createSchedTask} disabled={schedBusy || !schedForm.title.trim() || schedForm.prompt.trim().length<3}>{schedBusy ? "Ekleniyor…" : "Görev Ekle"}</button>
                  </div>
                </div>
                {schedTasks.length > 0 && (
                  <div className="kb-list">
                    {schedTasks.map(t => (
                      <div key={t.id} className="kb-row" style={{opacity: t.enabled ? 1 : 0.5}}>
                        <Workflow size={13} />
                        <span className="kb-name" title={t.last_result || t.prompt}>{t.title}</span>
                        <span className="kb-meta">{t.schedule}{t.last_status ? " · " + t.last_status : ""}</span>
                        <button className="kb-del" title={t.enabled ? "Duraklat" : "Etkinleştir"} onClick={()=>toggleSchedTask(t)}>{t.enabled ? <CircleDot size={13}/> : <Check size={13}/>}</button>
                        <button className="kb-del" title="Sil" onClick={()=>deleteSchedTask(t.id)}><Trash2 size={13} /></button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="m-section">
              <div className="ms-label">Model Sağlayıcıları</div>
              {PROV_ORDER.map(id => {
                const meta = PROV_META[id]; const p = providers[id];
                return (
                  <div key={id} className="prov-card">
                    <div className="pc-head">
                      <div className="pc-ic">{React.createElement(meta.icon,{size:18})}</div>
                      <div className="pc-txt"><div className="t">{meta.label} {provReady(id) && <Check size={13} color="#38e1d6" style={{verticalAlign:"middle"}}/>}</div><div className="h">{meta.hint}</div></div>
                    </div>
                    <div className="pc-inputs">
                      <label>Base URL</label>
                      <input value={p.baseUrl} onChange={(e)=>setProv(id,{baseUrl:e.target.value})} placeholder="base url" />
                      {meta.keyLabel && (<>
                        <label>{meta.keyLabel}</label>
                        <input type="password" value={p.apiKey} onChange={(e)=>setProv(id,{apiKey:e.target.value})} placeholder="••••••••" />
                      </>)}
                      {id === "gateway" && (
                        <div className="oidc-row">
                          {auth ? (<>
                            <span className="oidc-badge"><Check size={12} /> {auth.email || "oturum açık"}</span>
                            <button className="oidc-btn ghost" onClick={logoutOidc}>Çıkış</button>
                          </>) : (
                            <button className="oidc-btn" onClick={loginOidc}><Link2 size={13} /> Keycloak ile Giriş</button>
                          )}
                        </div>
                      )}
                      {id === "gateway" && gatewayInfo && (
                        <div className="kb-hint" style={{margin:"8px 0 0"}}>
                          Default: <b>{gatewayInfo.default || "?"}</b> - Görsel: <b>{gatewayInfo.vision || "?"}</b> - Uzak görsel: <b>{gatewayInfo.remote_images ? "açık" : "kapalı"}</b> - Ses kuyruğu: <b>{gatewayInfo.voice_queue ? "açık" : "kapalı"}</b>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="m-section">
              <div className="ms-label">Ses (Whisper STT + TTS)</div>
              <div className="prov-card">
                <div className="pc-head">
                  <div className="pc-ic"><Waves size={18} /></div>
                  <div className="pc-txt"><div className="t">Gerçek ses</div><div className="h">Mikrofon → Whisper, yanıt → TTS. Orb gerçek dalgaya tepki verir. Kapalıyken tarayıcının yerleşik sesi kullanılır.</div></div>
                  <button className={"reason-toggle" + (voiceCfg.real ? " on" : "")} onClick={()=>setVc({ real: !voiceCfg.real })} title="Gerçek ses">
                    <span className="rt-switch" />
                  </button>
                </div>
                {voiceCfg.real && (
                  <>
                    <div className="pc-head" style={{marginTop:12}}>
                      <div className="pc-ic"><Workflow size={18} /></div>
                      <div className="pc-txt"><div className="t">Kuyruk</div><div className="h">BullMQ job takibi.</div></div>
                      <button className={"reason-toggle" + (voiceCfg.queued ? " on" : "")} onClick={()=>setVc({ queued: !voiceCfg.queued })} title="Kuyruk">
                        <span className="rt-switch" />
                      </button>
                    </div>
                    <div className="pc-inputs">
                      <label>STT ucu (Whisper)</label>
                      <input value={voiceCfg.sttUrl} onChange={(e)=>setVc({ sttUrl: e.target.value })} placeholder="http://localhost:8088/stt" />
                      <label>TTS ucu</label>
                      <input value={voiceCfg.ttsUrl} onChange={(e)=>setVc({ ttsUrl: e.target.value })} placeholder="http://localhost:8088/tts" />
                      {voiceCfg.queued && (
                        <>
                          <label>Job ucu</label>
                          <input value={voiceCfg.jobUrl} onChange={(e)=>setVc({ jobUrl: e.target.value })} placeholder="/v1/voice/jobs" />
                        </>
                      )}
                      <label>Ses (voice)</label>
                      <input value={voiceCfg.voice} onChange={(e)=>setVc({ voice: e.target.value })} placeholder="alloy" />
                    </div>
                  </>
                )}
              </div>
            </div>

            <div className="m-section">
              <div className="ms-label">Ajan Katmanı (opsiyonel)</div>
              {AGENTS.map(a => (
                <div key={a.id} className={"agent-card" + (a.id===agent?" sel":"")} onClick={()=>{ setAgent(a.id); setAgentUrl(a.ph||""); }}>
                  <div className="ac-ic">{React.createElement(a.icon,{size:18})}</div>
                  <div className="ac-txt"><div className="t">{a.name}</div><div className="d">{a.desc}</div></div>
                  {a.id===agent && <Check size={18} color="#38e1d6" />}
                </div>
              ))}
              {agent !== "direct" && (
                <div className="pc-inputs" style={{marginTop:4}}>
                  <label>Ajan Endpoint</label>
                  <input value={agentUrl} onChange={(e)=>setAgentUrl(e.target.value)} placeholder={AGENTS.find(a=>a.id===agent).ph} />
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ThinkTrace({ text, live }) {
  const [stage, setStage] = useState(0);
  const [open, setOpen] = useState(true);
  const hasReal = !!(text && text.trim());
  useEffect(() => {
    if (hasReal) return;            // gerçek düşünce akışı varsa sahte adımları çalıştırma
    const iv = setInterval(() => setStage(s => (s >= THINK_STEPS.length ? s : s + 1)), 520);
    return () => clearInterval(iv);
  }, [hasReal]);

  // gerçek düşünme token'ları (Ollama/Gemini/Anthropic-direkt)
  if (hasReal) {
    return (
      <div className="think-trace">
        <div className="think-head" style={{ cursor: "pointer" }} onClick={() => setOpen(o => !o)}>
          <Brain size={13} /> {live ? "Düşünülüyor…" : "Muhakeme"} {!live && <ChevronDown size={12} style={{ transform: open ? "none" : "rotate(-90deg)" }} />}
        </div>
        {open && <div className="think-real">{text}</div>}
      </div>
    );
  }
  if (!live) return null;           // geçmişte sahte iz gösterme
  return (
    <div className="think-trace">
      <div className="think-head"><Brain size={13} /> Düşünülüyor</div>
      {THINK_STEPS.map((s, i) => i < stage && (
        <div key={i} className={"think-step" + (i < stage - 1 ? " done" : "")} style={{ animationDelay: (i * 0.05) + "s" }}>
          <span className="sc">{i < stage - 1 ? <Check size={10} /> : null}</span>{s}
        </div>
      ))}
    </div>
  );
}

/* ============================ MARKDOWN ============================ */
// Hafif, bağımlılıksız markdown render: başlık, liste, alıntı, satıriçi (kod/kalın/italik/link)
// ve kopyalanabilir kod blokları.
function renderInline(text, kb) {
  const nodes = [];
  let last = 0, i = 0;
  const re = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*\n]+\*)|(\[[^\]]+\]\([^)]+\))/g;
  let m;
  while ((m = re.exec(text))) {
    if (m.index > last) nodes.push(text.slice(last, m.index));
    const tok = m[0];
    if (tok.startsWith("`")) nodes.push(<code className="md-ic" key={kb + i}>{tok.slice(1, -1)}</code>);
    else if (tok.startsWith("**")) nodes.push(<strong key={kb + i}>{tok.slice(2, -2)}</strong>);
    else if (tok.startsWith("*")) nodes.push(<em key={kb + i}>{tok.slice(1, -1)}</em>);
    else {
      const mm = /\[([^\]]+)\]\(([^)]+)\)/.exec(tok);
      const href = safeLinkHref(mm[2]);
      nodes.push(href ? <a key={kb + i} href={href} target="_blank" rel="noreferrer">{mm[1]}</a> : <span key={kb + i}>{mm[1]}</span>);
    }
    last = m.index + tok.length; i++;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

function safeLinkHref(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  try {
    const u = new URL(raw, window.location.href);
    return ["http:", "https:", "mailto:"].includes(u.protocol) ? u.href : "";
  } catch {
    return "";
  }
}

// Önizlenebilir diller (canvas panelinde canlı render edilir)
const PREVIEWABLE = { html: "html", svg: "svg", xml: "svg", mermaid: "mermaid", mmd: "mermaid" };
function CodeBlock({ lang, code, onArtifact }) {
  const [copied, setCopied] = useState(false);
  const copy = () => { try { navigator.clipboard && navigator.clipboard.writeText(code); setCopied(true); setTimeout(()=>setCopied(false), 1500); } catch (e) {} };
  const previewable = PREVIEWABLE[(lang || "").toLowerCase()];
  return (
    <div className="code-block">
      <div className="code-bar">
        <span className="lang">{lang || "kod"}</span>
        <div style={{display:"flex",gap:4}}>
          {previewable && onArtifact && (
            <button className="code-copy" onClick={()=>onArtifact({ type: previewable, code, lang })}><Eye size={12} /> Önizle</button>
          )}
          <button className="code-copy" onClick={copy}>{copied ? <><Check size={12} /> Kopyalandı</> : <><Copy size={12} /> Kopyala</>}</button>
        </div>
      </div>
      <pre><code>{code}</code></pre>
    </div>
  );
}

function Markdown({ text, onArtifact }) {
  const out = [];
  const re = /```(\w*)\n?([\s\S]*?)```/g;
  let last = 0, m, k = 0;
  const pushText = (seg, key) => {
    const blocks = seg.split(/\n{2,}/);
    blocks.forEach((blk, bi) => {
      const t = blk.trim(); if (!t) return;
      const kb = key + "-" + bi + "-";
      const h = /^(#{1,4})\s+(.*)$/.exec(t);
      if (h) { const Tag = "h" + h[1].length; out.push(React.createElement(Tag, { key: kb + "h", className: "md" }, renderInline(h[2], kb))); return; }
      const lines = t.split("\n");
      if (lines.every(l => /^\s*[-*]\s+/.test(l))) {
        out.push(<ul className="md" key={kb + "ul"}>{lines.map((l, li) => <li key={li}>{renderInline(l.replace(/^\s*[-*]\s+/, ""), kb + li)}</li>)}</ul>); return;
      }
      if (lines.every(l => /^\s*\d+\.\s+/.test(l))) {
        out.push(<ol className="md" key={kb + "ol"}>{lines.map((l, li) => <li key={li}>{renderInline(l.replace(/^\s*\d+\.\s+/, ""), kb + li)}</li>)}</ol>); return;
      }
      if (lines.every(l => /^\s*>\s?/.test(l))) {
        out.push(<blockquote className="md" key={kb + "bq"}>{renderInline(lines.map(l => l.replace(/^\s*>\s?/, "")).join(" "), kb)}</blockquote>); return;
      }
      const parts = [];
      lines.forEach((l, li) => { if (li) parts.push(<br key={"br" + li} />); parts.push(...renderInline(l, kb + li)); });
      out.push(<p className="md" key={kb + "p"}>{parts}</p>);
    });
  };
  while ((m = re.exec(text))) {
    if (m.index > last) pushText(text.slice(last, m.index), "t" + k);
    out.push(<CodeBlock key={"c" + k} lang={m[1]} code={m[2].replace(/\n$/, "")} onArtifact={onArtifact} />);
    last = m.index + m[0].length; k++;
  }
  if (last < text.length) pushText(text.slice(last), "t" + k);
  return <div className="md">{out}</div>;
}
