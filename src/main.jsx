import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  ArrowLeft, ArrowRight, Bookmark, Check, ChevronDown,
  Command, Languages, Menu, Moon, Search, Share2, Sparkles, Sun, X
} from 'lucide-react'
import './styles.css'

const englishChapters = {
  1: [
    ['起初，神創造天地。', 'In the beginning God created the heavens and the earth.'],
    ['地是空虛混沌，淵面黑暗；神的靈運行在水面上。', 'Now the earth was formless and empty, darkness was over the surface of the deep.'],
    ['神說：「要有光」，就有了光。', 'And God said, “Let there be light,” and there was light.'],
    ['神看光是好的，就把光暗分開了。', 'God saw that the light was good, and he separated the light from the darkness.'],
    ['神稱光為「晝」，稱暗為「夜」。有晚上，有早晨，這是第一日。', 'God called the light “day,” and the darkness he called “night.” And there was evening, and there was morning—the first day.'],
    ['神說：「眾水之間要有穹蒼，把水和水分開。」', 'And God said, “Let there be a vault between the waters to separate water from water.”'],
    ['神就造出穹蒼，把穹蒼以下的水和穹蒼以上的水分開。事就這樣成了。', 'So God made the vault and separated the water under the vault from the water above it.'],
    ['神稱穹蒼為「天」。有晚上，有早晨，這是第二日。', 'God called the vault “sky.” And there was evening, and there was morning—the second day.'],
    ['神說：「天下的水要聚在一處，使乾地露出來。」事就這樣成了。', 'And God said, “Let the water under the sky be gathered to one place, and let dry ground appear.”'],
    ['神稱乾地為「地」，稱聚在一起的水為「海」。神看這是好的。', 'God called the dry ground “land,” and the gathered waters he called “seas.” And God saw that it was good.'],
    ['神說：「地要長出青草、結種子的菜蔬和結果子的樹木。」', 'Then God said, “Let the land produce vegetation: seed-bearing plants and trees.”'],
    ['於是地長出了青草和各樣結種子的菜蔬，並各樣結果子的樹木。神看這是好的。', 'The land produced vegetation: plants bearing seed and trees bearing fruit. And God saw that it was good.'],
    ['有晚上，有早晨，這是第三日。', 'And there was evening, and there was morning—the third day.'],
    ['神說：「天上要有光體，可以分晝夜，作記號，定節令、日子、年份。」', 'And God said, “Let there be lights in the vault of the sky to separate the day from the night.”'],
    ['神造了兩個大光體，大的管理白晝，小的管理黑夜，又造了眾星。', 'God made two great lights—the greater light to govern the day and the lesser light to govern the night.'],
    ['神把這些光體擺列在天空，照耀地上，管理晝夜，分別光暗。神看這是好的。', 'God set them in the vault of the sky to give light on the earth, to govern the day and the night.'],
    ['有晚上，有早晨，這是第四日。', 'And there was evening, and there was morning—the fourth day.'],
    ['神說：「水要滋生許多有生命之物；天空要有飛鳥飛翔。」', 'And God said, “Let the water teem with living creatures, and let birds fly above the earth.”'],
    ['神就造出大魚和水中各樣有生命的動物，又造各樣飛鳥。神看這是好的。', 'So God created the great creatures of the sea and every winged bird. And God saw that it was good.'],
    ['神賜福給這一切，說：「要繁殖增多，充滿海洋；飛鳥也要在地上增多。」', 'God blessed them and said, “Be fruitful and increase in number and fill the water in the seas.”'],
    ['有晚上，有早晨，這是第五日。', 'And there was evening, and there was morning—the fifth day.'],
    ['神說：「地要生出各樣有生命之物，各從其類。」事就這樣成了。', 'And God said, “Let the land produce living creatures according to their kinds.”'],
    ['神造了地上各樣的走獸、牲畜和爬行動物。神看這是好的。', 'God made the wild animals, the livestock, and all the creatures that move along the ground.'],
    ['神說：「我們要照著我們的形像，按著我們的樣式造人。」', 'Then God said, “Let us make mankind in our image, in our likeness.”'],
    ['神就照著自己的形像創造人；照著神的形像創造他；創造了男人和女人。', 'So God created mankind in his own image; male and female he created them.'],
    ['神賜福給他們，說：「要繁衍增多，遍滿這地，治理它。」', 'God blessed them and said to them, “Be fruitful and increase in number; fill the earth and subdue it.”'],
    ['神說：「看哪，我把遍地一切結種子的菜蔬和一切結果子的樹木都賜給你們作食物。」', 'Then God said, “I give you every seed-bearing plant and every tree that has fruit with seed in it.”'],
    ['神看一切所造的都非常好。有晚上，有早晨，這是第六日。', 'God saw all that he had made, and it was very good. And there was evening, and there was morning—the sixth day.']
  ],
  2: [
    ['天地萬物都造齊了。', 'Thus the heavens and the earth were completed in all their vast array.'],
    ['到第七日，神完成了他所做的工，就在第七日歇了他一切的工。', 'By the seventh day God had finished the work he had been doing; so on the seventh day he rested.'],
    ['神賜福給第七日，定為聖日，因為這日神歇了他一切創造的工。', 'Then God blessed the seventh day and made it holy.'],
    ['這是創造天地的來歷。耶和華神造地和天的時候，', 'This is the account of the heavens and the earth when they were created.'],
    ['野地還沒有草木，田間還沒有菜蔬，因為耶和華神還沒有降雨在地上。', 'Now no shrub had yet appeared on the earth and no plant had yet sprung up.'],
    ['但有霧氣從地上騰，滋潤整個地面。', 'But streams came up from the earth and watered the whole surface of the ground.'],
    ['耶和華神用地上的塵土造人，將生命之氣吹進他的鼻孔，這人就成了有靈的活人。', 'Then the Lord God formed a man from the dust and breathed into his nostrils the breath of life.']
  ],
  3: [
    ['耶和華神所造的，惟有蛇比田野一切的活物更狡猾。', 'Now the serpent was more crafty than any of the wild animals the Lord God had made.'],
    ['女人對蛇說：「園中樹上的果子，我們都可以吃。」', 'The woman said to the serpent, “We may eat fruit from the trees in the garden.”'],
    ['只是園當中那棵樹的果子，神曾說不可吃，也不可摸，免得你們死。', 'But God did say, “You must not eat fruit from the tree that is in the middle of the garden.”'],
    ['蛇對女人說：「你們不一定死。」', '“You will not certainly die,” the serpent said to the woman.'],
    ['於是女人見那棵樹的果子好作食物，也悅人的眼目，就摘下果子來吃了。', 'When the woman saw that the fruit of the tree was good for food and pleasing to the eye, she took some and ate it.']
  ]
}

const bookNames = ['創世記','出埃及記','利未記','民數記','申命記','約書亞記','士師記','路得記','撒母耳記上','撒母耳記下','列王紀上','列王紀下','歷代志上','歷代志下','以斯拉記','尼希米記','以斯帖記','約伯記','詩篇','箴言','傳道書','雅歌','以賽亞書','耶利米書','耶利米哀歌','以西結書','但以理書','何西阿書','約珥書','阿摩司書','俄巴底亞書','約拿書','彌迦書','那鴻書','哈巴谷書','西番雅書','哈該書','撒迦利亞書','瑪拉基書','馬太福音','馬可福音','路加福音','約翰福音','使徒行傳','羅馬書','哥林多前書','哥林多後書','加拉太書','以弗所書','腓利比書','歌羅西書','帖撒羅尼迦前書','帖撒羅尼迦後書','提摩太前書','提摩太後書','提多書','腓利門書','希伯來書','雅各書','彼得前書','彼得後書','約翰壹書','約翰貳書','約翰參書','猶大書','啟示錄']
const bookShort = ['創','出','利','民','申','書','士','得','撒上','撒下','王上','王下','代上','代下','拉','尼','斯','伯','詩','箴','傳','歌','賽','耶','哀','結','但','何','珥','摩','俄','拿','彌','鴻','哈','番','該','亞','瑪','太','可','路','約','徒','羅','林前','林後','加','弗','腓','西','帖前','帖後','提前','提後','多','門','來','雅','彼前','彼後','約壹','約貳','約參','猶','啟']
const bookIds = ['GEN','EXO','LEV','NUM','DEU','JOS','JDG','RUT','1SA','2SA','1KI','2KI','1CH','2CH','EZR','NEH','EST','JOB','PSA','PRO','ECC','SNG','ISA','JER','LAM','EZK','DAN','HOS','JOL','AMO','OBA','JON','MIC','NAM','HAB','ZEP','HAG','ZEC','MAL','MAT','MRK','LUK','JHN','ACT','ROM','1CO','2CO','GAL','EPH','PHP','COL','1TH','2TH','1TI','2TI','TIT','PHM','HEB','JAS','1PE','2PE','1JN','2JN','3JN','JUD','REV']
const chapterCounts = [50,40,27,36,34,24,21,4,31,24,22,25,29,36,10,13,10,42,150,31,12,8,66,52,5,48,12,14,3,9,1,4,7,3,3,3,2,14,4,28,16,24,21,28,16,16,13,6,6,4,4,5,3,6,4,3,1,13,5,5,3,5,1,1,1,1,22]
const bookEnglish = ['Genesis','Exodus','Leviticus','Numbers','Deuteronomy','Joshua','Judges','Ruth','1 Samuel','2 Samuel','1 Kings','2 Kings','1 Chronicles','2 Chronicles','Ezra','Nehemiah','Esther','Job','Psalms','Proverbs','Ecclesiastes','Song of Songs','Isaiah','Jeremiah','Lamentations','Ezekiel','Daniel','Hosea','Joel','Amos','Obadiah','Jonah','Micah','Nahum','Habakkuk','Zephaniah','Haggai','Zechariah','Malachi','Matthew','Mark','Luke','John','Acts','Romans','1 Corinthians','2 Corinthians','Galatians','Ephesians','Philippians','Colossians','1 Thessalonians','2 Thessalonians','1 Timothy','2 Timothy','Titus','Philemon','Hebrews','James','1 Peter','2 Peter','1 John','2 John','3 John','Jude','Revelation']
const books = bookNames.map((name, index) => ({name, id:bookIds[index], short:bookShort[index], chapters:chapterCounts[index], english:bookEnglish[index], testament:index < 39 ? 'old' : 'new'}))

function IconButton({ label, children, onClick, active=false }) {
  return <button className={`icon-button ${active?'active':''}`} aria-label={label} onClick={onClick}>{children}</button>
}

function App() {
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'dark')
  const [language, setLanguage] = useState('zh')
  const [bookIndex, setBookIndex] = useState(0)
  const [chapter, setChapter] = useState(1)
  const [testament, setTestament] = useState('old')
  const [bookOpen, setBookOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [saved, setSaved] = useState(new Set())
  const [reading, setReading] = useState(1)
  const [bookData, setBookData] = useState({zh:[],en:[]})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('theme', theme)
  }, [theme])
  useEffect(() => {
    let active = true
    setLoading(true)
    const id = books[bookIndex].id
    Promise.all([
      fetch(`https://bible-api.com/data/cuv/${id}/${chapter}`).then(response => response.json()),
      fetch(`https://bible-api.com/data/web/${id}/${chapter}`).then(response => response.json())
    ]).then(([zh,en]) => {
      if (active) { setBookData({zh:zh.verses||[],en:en.verses||[]}); setLoading(false) }
    }).catch(() => active && setLoading(false))
    return () => { active = false }
  }, [bookIndex, chapter])
  useEffect(() => {
    const onKey = e => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setSearchOpen(true) }
      if (e.key === 'Escape') { setSearchOpen(false); setBookOpen(false) }
      if (e.key === 'ArrowRight' && !searchOpen) changeChapter(1)
      if (e.key === 'ArrowLeft' && !searchOpen) changeChapter(-1)
    }
    addEventListener('keydown', onKey); return () => removeEventListener('keydown', onKey)
  })

  const currentBook = books[bookIndex]
  const verses = useMemo(() => bookData.zh.map((item,index) => [item.text, bookData.en[index]?.text || '']), [bookData])
  const results = useMemo(() => query.trim() ? bookData.zh.filter(item=>item.text.includes(query.trim())).slice(0,12).map(item=>({text:item.text,chapter:item.chapter,verse:item.verse,bookIndex})) : [], [query, bookData, bookIndex])
  const progress = Math.round((reading / verses.length) * 100)
  const selectBook = index => { setBookIndex(index); setChapter(1); setReading(1); setLanguage('zh'); setBookOpen(false); scrollTo({top:0,behavior:'smooth'}) }
  const changeChapter = delta => {
    if (delta < 0 && chapter === 1 && bookIndex > 0) { setBookIndex(bookIndex-1); setChapter(books[bookIndex-1].chapters) }
    else if (delta > 0 && chapter === currentBook.chapters && bookIndex < books.length-1) { setBookIndex(bookIndex+1); setChapter(1) }
    else setChapter(Math.max(1, Math.min(currentBook.chapters, chapter + delta)))
    setReading(1); scrollTo({top:0,behavior:'smooth'})
  }
  const toggleSaved = n => setSaved(s => { const key=`${bookIndex}-${chapter}-${n}`, next=new Set(s); next.has(key)?next.delete(key):next.add(key); return next })

  return <div className="app-shell">
    <div className="ambient ambient-one"/><div className="ambient ambient-two"/>
    <header className="topbar">
      <div className="top-actions">
        <span className="mobile-library-button"><IconButton label="選擇書卷" onClick={()=>setBookOpen(true)}><Menu size={17}/></IconButton></span>
        <button className="search-trigger" onClick={()=>setSearchOpen(true)}><Search size={16}/><span>搜尋經文</span><kbd><Command size={11}/> K</kbd></button>
        <IconButton label="切換深淺色" onClick={()=>setTheme(theme==='dark'?'light':'dark')}>{theme==='dark'?<Sun size={17}/>:<Moon size={17}/>}</IconButton>
      </div>
    </header>

    <main className="layout">
      <aside className="sidebar">
        <div className="side-label">正在閱讀</div>
        <button className="book-select" onClick={()=>setBookOpen(!bookOpen)} aria-expanded={bookOpen}>
          <span><small>{currentBook.testament==='old'?'舊約':'新約'}</small>{currentBook.name}</span><ChevronDown size={17}/>
        </button>
        <div className="chapter-grid">
          {Array.from({length:currentBook.chapters},(_,i)=>i+1).map(n=><button key={n} className={chapter===n?'current':''} onClick={()=>{setChapter(n);setReading(1);scrollTo({top:0,behavior:'smooth'})}}>{n}</button>)}
        </div>
        <div className="side-progress">
          <div className="progress-ring" style={{'--progress':`${progress*3.6}deg`}}><span>{progress}%</span></div>
          <div><strong>閱讀進度</strong><span>{currentBook.name} {chapter} · {reading}/{verses.length} 節</span></div>
        </div>
      </aside>

      <article className="reader" key={`${bookIndex}-${chapter}`}>
        <h1>{language==='en' ? currentBook.english : language==='both' ? `${currentBook.name} / ${currentBook.english}` : currentBook.name} <em>{String(chapter).padStart(2,'0')}</em></h1>
        <div className="reader-tools">
          <div className="language-switch" data-active={language}>
            <span className="switch-glider"/>
            <button onClick={()=>setLanguage('zh')} aria-pressed={language==='zh'}>中文</button>
            <button onClick={()=>setLanguage('en')} aria-pressed={language==='en'}>英文</button>
            <button onClick={()=>setLanguage('both')} aria-pressed={language==='both'}>雙語</button>
          </div>
        </div>
        <div className={`verses ${loading?'loading':''}`}>
          {loading&&<div className="chapter-loading"><span/><span/><span/></div>}
          {verses.map((verse,i)=>{
            const n=i+1, isSaved=saved.has(`${bookIndex}-${chapter}-${n}`)
            return <section className={`verse ${reading===n?'reading':''}`} key={n} style={{'--delay':`${Math.min(i,12)*35}ms`}} onClick={()=>setReading(n)}>
              <span className="verse-number">{String(n).padStart(2,'0')}</span>
              <div className="verse-copy">
                {language!=='en'&&<p lang="zh-Hant">{verse[0]}</p>}
                {language!=='zh'&&verse[1]&&<p className={`english ${language==='en'?'english-only':''}`} lang="en">{verse[1]}</p>}
              </div>
              <div className="verse-actions">
                <IconButton label={isSaved?'取消收藏':'收藏經文'} active={isSaved} onClick={e=>{e.stopPropagation();toggleSaved(n)}}><Bookmark size={15} fill={isSaved?'currentColor':'none'}/></IconButton>
                <IconButton label="分享經文"><Share2 size={15}/></IconButton>
              </div>
            </section>
          })}
        </div>
        <nav className="chapter-nav">
          <button onClick={()=>changeChapter(-1)} disabled={chapter===1&&bookIndex===0}><ArrowLeft size={17}/><span><small>上一章</small>{chapter===1?(bookIndex===0?'—':`${books[bookIndex-1].name} ${books[bookIndex-1].chapters}`):`${currentBook.name} ${chapter-1}`}</span></button>
          <button onClick={()=>changeChapter(1)} disabled={chapter===currentBook.chapters&&bookIndex===books.length-1}><span><small>下一章</small>{chapter===currentBook.chapters?(bookIndex===books.length-1?'—':`${books[bookIndex+1].name} 1`):`${currentBook.name} ${chapter+1}`}</span><ArrowRight size={17}/></button>
        </nav>
      </article>

      <aside className="reflection">
        <div className="reflection-head"><Sparkles size={15}/><span>今日默想</span></div>
        <blockquote>「神說：要有光，<br/>就有了光。」</blockquote>
        <p>在一切還未清晰以前，光已經被說出來。今天，留一點空間給新的開始。</p>
        <div className="reflection-meta"><span>{currentBook.name} {chapter}:{Math.min(3,verses.length)}</span><span>07 · 27</span></div>
        <button className="complete"><span><Check size={14}/></span>完成今日閱讀</button>
      </aside>
    </main>

    <div className={`overlay ${bookOpen?'show':''}`} onClick={()=>setBookOpen(false)}>
      <div className="library" onClick={e=>e.stopPropagation()}>
        <div className="panel-head"><div><h2>選擇書卷</h2></div><IconButton label="關閉" onClick={()=>setBookOpen(false)}><X size={18}/></IconButton></div>
        <div className="testament-tabs" data-active={testament}><span className="testament-glider"/><button className={testament==='old'?'active':''} onClick={()=>setTestament('old')}>舊約 · 39</button><button className={testament==='new'?'active':''} onClick={()=>setTestament('new')}>新約 · 27</button></div>
        <div className="book-list" key={testament}>{books.map((book,i)=>({book,i})).filter(({book})=>book.testament===testament).map(({book,i})=><button className={i===bookIndex?'active':''} key={book.name} onClick={()=>selectBook(i)}><span>{String(i+1).padStart(2,'0')}</span><strong>{book.name}</strong><small>{book.chapters} 章</small></button>)}</div>
      </div>
    </div>

    <div className={`search-overlay ${searchOpen?'show':''}`}>
      <button className="search-close" aria-label="關閉搜尋" onClick={()=>setSearchOpen(false)}><X/></button>
      <div className="search-dialog">
        <div className="search-input"><Search size={21}/><input autoFocus={searchOpen} value={query} onChange={e=>setQuery(e.target.value)} placeholder="搜尋一段經文、詞語或章節…"/><kbd>ESC</kbd></div>
        <div className="search-content">
          {!query&&<div className="search-empty"><span><Sparkles/></span><h3>你想尋找什麼？</h3><p>試試「光」、「起初」或「生命」</p></div>}
          {query&&results.length===0&&<div className="search-empty"><h3>沒有找到結果</h3><p>換一個關鍵字再試一次。</p></div>}
          {results.map(r=><button className="search-result" key={`${r.bookIndex}-${r.chapter}-${r.verse}`} onClick={()=>{setBookIndex(r.bookIndex);setChapter(r.chapter);setReading(r.verse);setLanguage('zh');setSearchOpen(false);setQuery('')}}><span>{currentBook.short} {r.chapter}:{r.verse}</span><p>{r.text}</p><ArrowRight size={15}/></button>)}
        </div>
      </div>
    </div>
    <div className="cursor-glow"/>
  </div>
}

createRoot(document.getElementById('root')).render(<App />)
